package gg.projects.mundoskasync;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.util.Timespan;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;

public class EffWaitAsync extends Effect {

    static {
        Skript.registerEffect(EffWaitAsync.class, "async wait %timespan%");
    }

    private Expression<Timespan> delay;

    @SuppressWarnings("unchecked")
    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
        delay = (Expression<Timespan>) exprs[0];
        getParser().setHasDelayBefore(Kleenean.TRUE);
        return true;
    }

    @Override
    protected void execute(Event e) { }

    @Override
    public TriggerItem walk(Event e) {
        debug(e, true);

        Timespan delayTime = this.delay.getSingle(e);
        if (delayTime == null) {
            return null;
        }

        TriggerItem next = getNext();
        ScriptContinuation.continueDetached(
            e,
            false,
            ScriptContinuation.ticks(delayTime),
            next == null ? null : () -> walk(next, e)
        );

        return null;
    }

    @Override
    public String toString(Event e, boolean debug) {
        return "async wait " + delay.toString(e, debug);
    }

}
