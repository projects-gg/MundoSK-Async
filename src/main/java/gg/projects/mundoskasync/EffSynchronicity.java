package gg.projects.mundoskasync;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;

public class EffSynchronicity extends Effect {

    static {
        Skript.registerEffect(EffSynchronicity.class, "async", "sync");
    }

    private boolean isSync;

    @Override
    public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, SkriptParser.ParseResult parseResult) {
        isSync = matchedPattern == 1;
        return true;
    }

    @Override
    protected void execute(Event e) { }

    @Override
    public TriggerItem walk(Event e) {
        Runnable task = () -> {
            TriggerItem next = getNext();
            if (next != null) {
                walk(next, e);
            }
        };

        if (isSync) {
            Scheduling.sync(task);
        } else {
            Scheduling.async(task);
        }
        return null;
    }

    @Override
    public String toString(Event e, boolean debug) {
        return isSync ? "sync" : "async";
    }
}
