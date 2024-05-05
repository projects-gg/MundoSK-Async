package gg.projects.mundoskasync;

import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import org.bukkit.event.Event;

public class FakeSkriptEvent extends SkriptEvent {

    private final String name;

    public FakeSkriptEvent(String name) {
        this.name = name;
    }

    @Override
    public boolean init(Literal<?>[] args, int matchedPattern, ParseResult parseResult) {
        throw new RuntimeException();
    }

    @Override
    public boolean check(Event e) {
        throw new RuntimeException();
    }

    @Override
    public String toString(Event e, boolean debug) {
        return name;
    }

}
