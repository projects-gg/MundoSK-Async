package gg.projects.mundoskasync;

import ch.njol.skript.ScriptLoader;
import ch.njol.skript.Skript;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.Section;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.lang.TriggerSection;
import ch.njol.skript.lang.parser.ParserInstance;
import ch.njol.skript.util.Timespan;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.skriptlang.skript.lang.script.Script;

import java.util.ArrayList;
import java.util.List;

public class SecSynchronicity extends Section {

    static {
        Skript.registerSection(SecSynchronicity.class, "async [in %-timespan%]", "(sync|in %-timespan%)");
    }

    private boolean isSync;
    private Expression<Timespan> delay;
    private Trigger trigger;

    @SuppressWarnings("unchecked")
    @Override
    public boolean init(Expression<?>[] exprs,
                        int matchedPattern,
                        Kleenean isDelayed,
                        ParseResult parseResult,
                        SectionNode sectionNode,
                        List<TriggerItem> prevTriggerItems) {
        delay = (Expression<Timespan>) exprs[0];
        isSync = matchedPattern == 1;

        ParserInstance parser = getParser();

        List<TriggerSection> previousSections = parser.getCurrentSections();
        Kleenean previousDelay = parser.getHasDelayBefore();

        parser.setCurrentSections(new ArrayList<>());
        parser.setHasDelayBefore(Kleenean.FALSE);
        List<TriggerItem> triggerItems = ScriptLoader.loadItems(sectionNode);

        parser.setCurrentSections(previousSections);
        parser.setHasDelayBefore(previousDelay);

        Script script = parser.getCurrentScript();

        trigger = new Trigger(script, "async", new FakeSkriptEvent("async"), triggerItems);

        return true;
    }

    @Override
    protected TriggerItem walk(Event e) {
        Runnable runnable = () -> trigger.execute(e);

        if (this.delay == null) {
            if (isSync)
                Scheduling.sync(runnable);
            else
                Scheduling.async(runnable);
        } else {
            Timespan delay = this.delay.getSingle(e);
            if (delay == null)
                return null;
            long ticks = delay.getTicks();

            if (isSync)
                Scheduling.syncDelay(ticks, runnable);
            else
                Scheduling.asyncDelay(ticks, runnable);
        }

        return super.walk(e, false);
    }

    @Override
    public String toString(Event e, boolean debug) {
        return isSync ? "sync" : "async" + (delay == null ? "" : " " + delay.toString(e, debug));
    }

}
