package gg.projects.mundoskasync;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.logging.Level;

public class Scheduling {

    private static final BukkitScheduler scheduler = Bukkit.getScheduler();

    public static void sync(Runnable runnable) {
        scheduler.runTask(MundoSKAsync.getInstance(), wrapWithLogging(runnable, "sync"));
    }

    /*public static void async(Runnable runnable) {
        scheduler.runTaskAsynchronously(MundoSKAsync.getInstance(), wrapWithLogging(runnable, "async"));
    }*/
    public static void async(Runnable runnable) {
        TaskExecutor.executeAsync(runnable);
    }

    public static void syncDelay(long ticks, Runnable runnable) {
        scheduler.runTaskLater(MundoSKAsync.getInstance(), wrapWithLogging(runnable, "syncDelay"), ticks);
    }

    public static void asyncDelay(long ticks, Runnable runnable) {
        scheduler.runTaskLaterAsynchronously(MundoSKAsync.getInstance(), wrapWithLogging(runnable, "asyncDelay"), ticks);
    }

    private static Runnable wrapWithLogging(Runnable runnable, String context) {
        return () -> {
            try {
                runnable.run();
            } catch (Exception e) {
                MundoSKAsync.getInstance().getLogger().log(Level.SEVERE, "Error during " + context + " task", e);
            }
        };
    }
}