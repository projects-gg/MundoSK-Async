package gg.projects.mundoskasync;

import ch.njol.skript.effects.Delay;
import ch.njol.skript.util.Timespan;
import org.bukkit.event.Event;

final class ScriptContinuation {

    private ScriptContinuation() {
    }

    static void continueDetached(Event event, boolean sync, long delayTicks, Runnable continuation) {
        Delay.addDelayedEvent(event);

        SkriptLocalVariables.Snapshot localVariables = SkriptLocalVariables.detached(event);
        if (continuation == null) {
            localVariables.discard();
            return;
        }

        try {
            Scheduling.schedule(sync, delayTicks, () -> localVariables.run(continuation));
        } catch (RuntimeException ex) {
            localVariables.restore();
            throw ex;
        }

        // The continuation is scheduled for certain, so the local variable slot of this event now
        // belongs to it. An enclosing continuation scope on this thread must not clean the slot up
        // any more: the new continuation may already be running and have installed its variables.
        SkriptLocalVariables.markHandedOff(event);
    }

    static void forkCopied(Event event, boolean sync, long delayTicks, Runnable body) {
        SkriptLocalVariables.Snapshot localVariables = SkriptLocalVariables.copied(event);
        try {
            Scheduling.schedule(sync, delayTicks, () -> localVariables.run(body));
        } catch (RuntimeException ex) {
            localVariables.discard();
            throw ex;
        }
    }

    static long ticks(Timespan timespan) {
        return Math.max(0L, timespan.getAs(Timespan.TimePeriod.TICK));
    }
}
