package gg.projects.mundoskasync;

import ch.njol.skript.Skript;
import ch.njol.skript.effects.Delay;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.util.Timespan;
import ch.njol.skript.variables.Variables;
import ch.njol.util.Kleenean;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;

import java.util.logging.Level;

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

        Delay.addDelayedEvent(e);
        Object localVars = Variables.removeLocals(e);

        Timespan delay = this.delay.getSingle(e);
        if (delay == null) {
            return null;
        }

        Bukkit.getScheduler().runTaskLater(MundoSKAsync.getInstance(), () -> {
            try {
                if (localVars != null) {
                    Variables.setLocalVariables(e, localVars);
                }
                TriggerItem next = getNext();
                if (next != null) {
                    walk(next, e);
                }
            } catch (Exception ex) {
                MundoSKAsync.getInstance().getLogger().severe("Error in async wait continuation: " + ex.getMessage());
            } finally {
                Variables.removeLocals(e);
            }
        }, delay.getTicks_i());

        return null;
    }


    @Override
    public String toString(Event e, boolean debug) {
        return "async wait " + delay.toString(e, debug);
    }
}
