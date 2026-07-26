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

        List<TriggerItem> triggerItems;
        try {
            parser.setCurrentSections(new ArrayList<>());
            parser.setHasDelayBefore(Kleenean.FALSE);
            triggerItems = ScriptLoader.loadItems(sectionNode);
        } finally {
            parser.setCurrentSections(previousSections);
            parser.setHasDelayBefore(previousDelay);
        }

        Script script = parser.getCurrentScript();

        trigger = new Trigger(script, "async", new FakeSkriptEvent("async"), triggerItems);

        return true;
    }

    @Override
    protected TriggerItem walk(Event e) {
        Timespan delayTime = null;
        if (delay != null) {
            delayTime = delay.getSingle(e);
            if (delayTime == null) {
                return null;
            }
        }

        if (delayTime == null) {
            ScriptContinuation.forkCopied(e, isSync, 0L, () -> trigger.execute(e));
        } else {
            ScriptContinuation.forkCopied(
                e,
                isSync,
                ScriptContinuation.ticks(delayTime),
                () -> trigger.execute(e)
            );
        }

        return super.walk(e, false);
    }

    @Override
    public String toString(Event e, boolean debug) {
        String mode = isSync ? "sync" : "async";
        return mode + (delay == null ? "" : " in " + delay.toString(e, debug));
    }

}
