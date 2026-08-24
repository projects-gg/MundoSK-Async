package gg.projects.mundoskasync;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs main-thread script continuations through a per-tick budget instead of handing each one to
 * {@code BukkitScheduler#runTask} directly.
 *
 * <p>CraftBukkit's scheduler calls {@code parsePending()} inside its heartbeat drain loop, so a
 * zero-delay task submitted while the heartbeat is still draining runs in the <em>same</em> tick.
 * Async script threads pace their waits in wall-clock time, not ticks: once the main thread is
 * saturated, async producers keep submitting {@code sync} continuations into the tick currently
 * being drained, the heartbeat never runs dry, the tick never ends, and the watchdog eventually
 * kills the server. This class breaks that feedback loop: continuations are queued here and a
 * single repeating task drains the queue with a hard per-tick budget, so a flood degrades TPS
 * gracefully instead of freezing a tick forever.
 *
 * <p>Continuations still execute on the main thread, in submission order. The only observable
 * difference is that a continuation submitted mid-tick now starts at the next pump run (at most
 * one tick later) instead of possibly inside the current tick.
 */
final class SyncPump {

    /** Hard cap on continuations executed per tick, even if the time budget is not exhausted. */
    private static final int MAX_TASKS_PER_TICK = 256;

    /** Soft time budget per tick; checked between continuations, so one long task can overrun it. */
    private static final long MAX_NANOS_PER_TICK = 8_000_000L;

    /** Queue depth at which the pump starts warning about a producer flood. */
    private static final int BACKLOG_WARN_THRESHOLD = 4096;

    /** Minimum milliseconds between backlog warnings, to keep a sustained flood from spamming. */
    private static final long WARN_INTERVAL_MILLIS = 10_000L;

    private static final ConcurrentLinkedQueue<Runnable> QUEUE = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger SIZE = new AtomicInteger();

    private static volatile BukkitTask pumpTask;
    private static volatile long lastWarnAt;

    private SyncPump() {
    }

    static void start() {
        stop();
        pumpTask = Bukkit.getScheduler().runTaskTimer(MundoSKAsync.getInstance(), SyncPump::drain, 1L, 1L);
    }

    static void stop() {
        BukkitTask task = pumpTask;
        pumpTask = null;
        if (task != null) {
            task.cancel();
        }
        int dropped = 0;
        while (QUEUE.poll() != null) {
            SIZE.decrementAndGet();
            dropped++;
        }
        if (dropped > 0) {
            Bukkit.getLogger().warning("[MundoSK-Async] " + dropped
                + " sync script continuation(s) were still queued at shutdown and did not finish.");
        }
    }

    /**
     * Queues {@code runnable} for the main thread. Falls back to the Bukkit scheduler when the
     * pump is not running (enable/disable edges), so no continuation is ever silently lost.
     */
    static void submit(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        if (pumpTask == null) {
            Bukkit.getScheduler().runTask(MundoSKAsync.getInstance(), runnable);
            return;
        }
        QUEUE.add(runnable);
        int backlog = SIZE.incrementAndGet();
        if (backlog >= BACKLOG_WARN_THRESHOLD) {
            warnBacklog(backlog);
        }
    }

    private static void drain() {
        long deadline = System.nanoTime() + MAX_NANOS_PER_TICK;
        for (int ran = 0; ran < MAX_TASKS_PER_TICK; ran++) {
            Runnable runnable = QUEUE.poll();
            if (runnable == null) {
                return;
            }
            SIZE.decrementAndGet();
            try {
                runnable.run();
            } catch (Throwable throwable) {
                TaskExecutor.report("A sync script continuation ended with an error", throwable);
            }
            if (System.nanoTime() >= deadline) {
                return;
            }
        }
    }

    private static void warnBacklog(int backlog) {
        long now = System.currentTimeMillis();
        if (now - lastWarnAt < WARN_INTERVAL_MILLIS) {
            return;
        }
        lastWarnAt = now;
        Bukkit.getLogger().warning("[MundoSK-Async] " + backlog + " sync script continuations are queued;"
            + " a script is producing main-thread work faster than the server can run it."
            + " The pump is rate-limiting them to protect the tick loop.");
    }

}
