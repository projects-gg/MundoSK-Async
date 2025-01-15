package gg.projects.mundoskasync;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.logging.Level;

public class Scheduling {

    private static final BukkitScheduler scheduler = Bukkit.getScheduler();

    public static void sync(Runnable runnable) {
        scheduler.runTask(MundoSKAsync.getInstance(), runnable);
    }

    public static void async(Runnable runnable) {
        TaskExecutor.executeAsync(runnable);
    }

    public static void syncDelay(long ticks, Runnable runnable) {
        scheduler.runTaskLater(MundoSKAsync.getInstance(), runnable, ticks);
    }

    public static void asyncDelay(long ticks, Runnable runnable) {
        scheduler.runTaskLaterAsynchronously(MundoSKAsync.getInstance(), runnable, ticks);
    }
}
