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
