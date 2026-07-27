package gg.projects.mundoskasync;

import ch.njol.skript.variables.Variables;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayDeque;

final class SkriptLocalVariables {

    /**
     * {@code Variables.setLocalVariablesDetached(Object, boolean)}.
     * <p>
     * Skript wipes an event's local variables as soon as a trigger stops walking
     * ({@code Trigger#execute}). A continuation that resumes on another thread puts the very same
     * variables back under the very same event, so the two race: whenever the trigger's cleanup
     * lands after the continuation has re-installed them, the continuation carries on with no
     * local variables at all. Claiming the map tells Skript that another flow owns it and that
     * generic cleanups (the trigger's, or the one a {@code wait} runs after resuming) must leave
     * it alone. Claims are counted on the Skript side, so releasing one claim can never strip a
     * newer claim taken by the next continuation in a chain.
     * <p>
     * Bound as a {@link MethodHandle} because this plugin is compiled against upstream Skript,
     * which does not have the method; a {@code static final} handle invoked exactly is folded
     * into a direct call by the JIT, so the claim costs no more than a normal call. Without the
     * method the old (racy) behaviour is kept.
     */
    private static final MethodHandle SET_LOCAL_VARIABLES_DETACHED = findSetLocalVariablesDetached();

    /**
     * The continuation scopes currently running on this thread, innermost first.
     * <p>
     * A scope is pushed for the duration of every {@link Snapshot#run}. When the code inside it
     * hands the event's local variables to yet another continuation (an {@code async} or
     * {@code sync} effect inside the continuation), every scope of that event on this thread is
     * marked and its cleanup leaves the event's local variable slot alone: the next continuation
     * may already be running on another thread and have installed the very map this scope would
     * remove. Every enclosing scope is marked — not just the innermost — because a saturated pool
     * runs a continuation inline on the calling thread, nesting scopes of the same event.
     * <p>
     * Thread-confined by design: a mark is only ever read by the {@code finally} blocks of the
     * frames below the marking call on the same thread, so no synchronization is needed.
     */
    private static final ThreadLocal<ArrayDeque<Scope>> SCOPES = ThreadLocal.withInitial(ArrayDeque::new);

    /** Set once the claim has failed at runtime, so the warning is not repeated per event. */
    private static volatile boolean claimFailed;

    private SkriptLocalVariables() {
    }

    private static MethodHandle findSetLocalVariablesDetached() {
        try {
            return MethodHandles.lookup().findStatic(Variables.class, "setLocalVariablesDetached",
                MethodType.methodType(void.class, Object.class, boolean.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            Bukkit.getLogger().warning("[MundoSK-Async] This Skript build does not support claiming"
                + " detached local variables; local variables can be lost when an async"
                + " continuation resumes while its event is still being handled.");
            return null;
        }
    }

    /**
     * Claims local variables for a flow that continues off the main thread, or releases them
     * again once that flow is done with them.
     * <p>
     * Bookkeeping never propagates a failure: a claim that does not happen degrades to the old
     * racy behaviour, whereas throwing here would abort a script that is otherwise fine.
     */
    private static void claim(Object localVariables, boolean detached) {
        if (SET_LOCAL_VARIABLES_DETACHED == null || localVariables == null) {
            return;
        }
        try {
            SET_LOCAL_VARIABLES_DETACHED.invokeExact(localVariables, detached);
        } catch (Throwable throwable) {
            if (!claimFailed) {
                claimFailed = true;
                TaskExecutor.report("Could not claim local variables for an async continuation;"
                    + " local variables may be lost while an event is still being handled", throwable);
            }
        }
    }

    static Object copy(Event event) {
        return Variables.copyLocalVariables(event);
    }

    /**
     * Copies the event's local variables for a flow that runs alongside the trigger that is still
     * walking, and claims the copy so that trigger's cleanup cannot remove it.
     */
    static Snapshot copied(Event event) {
        return claimed(event, copy(event), false);
    }

    static Object remove(Event event) {
        return Variables.removeLocals(event);
    }

    /**
     * Takes the event's local variables away from the trigger that is stopping here and claims
     * them for a continuation that resumes on another thread.
     */
    static Snapshot detached(Event event) {
        return claimed(event, remove(event), true);
    }

    private static Snapshot claimed(Event event, Object localVariables, boolean detached) {
        claim(localVariables, true);
        return new Snapshot(event, localVariables, detached);
    }

    /**
     * Marks every continuation scope of {@code event} on this thread as handed off: the next
     * continuation now owns the event's local variable slot, so the scopes' cleanups must not
     * touch it. Called once the next continuation has been scheduled for certain — marking
     * before that would leak the variables if scheduling failed.
     */
    static void markHandedOff(Event event) {
        for (Scope scope : SCOPES.get()) {
            if (scope.event == event) {
                scope.handedOff = true;
            }
        }
    }

    static void restore(Event event, Object localVariables) {
        if (localVariables == null) {
            Variables.removeLocals(event);
        } else {
            Variables.setLocalVariables(event, localVariables);
        }
    }

    private static void runWith(Event event, Object localVariables, Runnable runnable, Scope scope) {
        Object previousLocalVariables = Variables.removeLocals(event);
        try {
            if (localVariables != null) {
                Variables.setLocalVariables(event, localVariables);
            }
            runnable.run();
        } finally {
            // When the body handed the slot to another continuation, that flow owns it now:
            // removing would race with it, and re-installing the previous variables would fight
            // its install. The previous map is dropped — a flow that was walking this event
            // concurrently was already broken the moment this fork swapped its variables out.
            if (!scope.handedOff) {
                Variables.removeLocals(event);
                if (previousLocalVariables != null) {
                    Variables.setLocalVariables(event, previousLocalVariables);
                }
            }
        }
    }

    /**
     * Installs the local variables handed to a continuation and runs it.
     * <p>
     * Unlike {@link #runWith}, nothing is swapped out and put back: the continuation is the sole
     * owner of these variables, and taking away whatever else is installed would corrupt a
     * trigger that is still running on the main thread for the same event.
     */
    private static void runDetached(Event event, Object localVariables, Runnable runnable, Scope scope) {
        try {
            if (localVariables != null) {
                Variables.setLocalVariables(event, localVariables);
            }
            runnable.run();
        } finally {
            // Cleaning up is this scope's job only while it still owns the slot. Once the body
            // handed the variables to the next continuation, removing here would wipe them out
            // from under that continuation — the exact race this class exists to prevent.
            if (!scope.handedOff) {
                Variables.removeLocals(event);
            }
        }
    }

    private static final class Scope {
        private final Event event;
        private boolean handedOff;

        private Scope(Event event) {
            this.event = event;
        }
    }

    static final class Snapshot {
        private final Event event;
        private final Object claimed;
        private final boolean detached;
        private Object localVariables;
        private boolean released;

        private Snapshot(Event event, Object localVariables, boolean detached) {
            this.event = event;
            this.claimed = localVariables;
            this.localVariables = localVariables;
            this.detached = detached;
        }

        void run(Runnable runnable) {
            Object localVariables = this.localVariables;
            this.localVariables = null;
            ArrayDeque<Scope> scopes = SCOPES.get();
            Scope scope = new Scope(event);
            scopes.push(scope);
            try {
                if (detached) {
                    runDetached(event, localVariables, runnable, scope);
                } else {
                    runWith(event, localVariables, runnable, scope);
                }
            } finally {
                scopes.pop();
                // The claim is released even after a hand-off: it only cancels this snapshot's
                // own claim, and the next continuation holds its own, so the map stays protected.
                release();
            }
        }

        private void release() {
            if (!released) {
                released = true;
                claim(claimed, false);
            }
        }

        void restore() {
            Object localVariables = this.localVariables;
            this.localVariables = null;
            release();
            SkriptLocalVariables.restore(event, localVariables);
        }

        void discard() {
            localVariables = null;
            release();
        }
    }
}
