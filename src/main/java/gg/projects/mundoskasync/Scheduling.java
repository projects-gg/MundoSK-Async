package gg.projects.mundoskasync;

import org.bukkit.Bukkit;

import java.util.Objects;

public final class Scheduling {

    private Scheduling() {
    }

    public static void schedule(boolean sync, long ticks, Runnable runnable) {
        if (normalizeDelay(ticks) == 0L) {
            if (sync) {
                sync(runnable);
            } else {
                async(runnable);
            }
        } else if (sync) {
            syncDelay(ticks, runnable);
        } else {
            asyncDelay(ticks, runnable);
        }
    }

    /** One Minecraft tick in milliseconds, used to convert tick delays for the async scheduler. */
    private static final long MILLIS_PER_TICK = 50L;

    public static void sync(Runnable runnable) {
        Bukkit.getScheduler().runTask(MundoSKAsync.getInstance(), requireRunnable(runnable));
    }

    // Async work is dispatched through the plugin's own bounded pool instead of
    // Bukkit's unbounded async scheduler, so a flood of async sections from a
    // hot event can no longer spawn an unbounded number of threads.
    public static void async(Runnable runnable) {
        TaskExecutor.executeAsync(requireRunnable(runnable));
    }

    public static void syncDelay(long ticks, Runnable runnable) {
        Bukkit.getScheduler().runTaskLater(MundoSKAsync.getInstance(), requireRunnable(runnable), normalizeDelay(ticks));
    }

    public static void asyncDelay(long ticks, Runnable runnable) {
        TaskExecutor.scheduleAsync(toMillis(normalizeDelay(ticks)), requireRunnable(runnable));
    }

    /**
     * Converts a tick delay to milliseconds, saturating instead of overflowing: a script is free
     * to ask for an absurd timespan, and wrapping around would schedule the work in the past.
     */
    private static long toMillis(long ticks) {
        return ticks > Long.MAX_VALUE / MILLIS_PER_TICK ? Long.MAX_VALUE : ticks * MILLIS_PER_TICK;
    }

    private static Runnable requireRunnable(Runnable runnable) {
        return Objects.requireNonNull(runnable, "runnable");
    }

    private static long normalizeDelay(long ticks) {
        return Math.max(0L, ticks);
    }

}
