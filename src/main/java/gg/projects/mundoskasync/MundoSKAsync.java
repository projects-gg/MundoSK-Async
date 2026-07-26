package gg.projects.mundoskasync;

import ch.njol.skript.Skript;
import ch.njol.skript.SkriptAddon;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

public class MundoSKAsync extends JavaPlugin {

    private static MundoSKAsync instance;
    private static SkriptAddon addonInstance;

    public MundoSKAsync() {
        if (instance != null && instance != this) {
            throw new IllegalStateException("MundoSK-Async is already initialized");
        }
        instance = this;
    }

    @Override
    public void onEnable() {
        instance = this;
        TaskExecutor.start();
        addonInstance = Skript.registerAddon(this);
        try {
            addonInstance.loadClasses("gg.projects.mundoskasync");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        TaskExecutor.shutdown();
        addonInstance = null;
        instance = null;
    }

    public static MundoSKAsync getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MundoSK-Async is not enabled");
        }
        return instance;
    }

    public static SkriptAddon getAddonInstance() {
        if (addonInstance == null) {
            throw new IllegalStateException("MundoSK-Async addon is not enabled");
        }
        return addonInstance;
    }

}
