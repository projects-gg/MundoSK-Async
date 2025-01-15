package gg.projects.mundoskasync;

import ch.njol.skript.Skript;
import ch.njol.skript.effects.Delay;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.variables.Variables;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;

import java.util.logging.Level;

public class EffSynchronicity extends Effect {

    static {
        Skript.registerEffect(EffSynchronicity.class, "async", "sync");
    }

    private boolean isSync;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
        isSync = matchedPattern == 1;

        getParser().setHasDelayBefore(Kleenean.TRUE);
        return true;
    }

    @Override
    protected void execute(Event e) { }

    @Override
    public TriggerItem walk(Event e) {
        debug(e, true);

        Object localVars = Variables.removeLocals(e);
        Delay.addDelayedEvent(e);

        Runnable runnable = () -> {
            try {
                if (localVars != null)
                    Variables.setLocalVariables(e, localVars);

                TriggerItem next = getNext();
                if (next != null)
                    walk(next, e);
            } catch (Exception ex) {
                MundoSKAsync.getInstance().getLogger().log(Level.SEVERE, "Error in synchronicity task", ex);
            } finally {
                Variables.removeLocals(e);
            }
        };

        if (isSync)
            Scheduling.sync(runnable);
        else
            Scheduling.async(runnable);

        return null;
    }


    @Override
    public String toString(Event e, boolean debug) {
        return isSync ? "sync" : "async";
    }

}
