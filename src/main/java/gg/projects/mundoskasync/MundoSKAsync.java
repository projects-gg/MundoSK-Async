package gg.projects.mundoskasync;

import ch.njol.skript.Skript;
import ch.njol.skript.SkriptAddon;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

public class MundoSKAsync extends JavaPlugin {

    private static MundoSKAsync instance;
    private static SkriptAddon addonInstance;

    public MundoSKAsync() {
        if (instance != null) {
            throw new IllegalStateException();
        }
        instance = this;
    }

    public void onEnable() {
        addonInstance = Skript.registerAddon(this);
        try {
            addonInstance.loadClasses("gg.projects.mundoskasync");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static MundoSKAsync getInstance() {
        if (instance == null)
            throw new IllegalStateException();
        return instance;
    }

    public static SkriptAddon getAddonInstance() {
        return addonInstance;
    }

}
