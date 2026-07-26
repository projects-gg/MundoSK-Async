package gg.projects.mundoskasync;

import org.bukkit.Bukkit;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Owns the bounded thread pool used for every asynchronous script continuation.
 *
 * <p>Historically the {@code async}/{@code async wait} sections were dispatched
 * through {@link org.bukkit.scheduler.BukkitScheduler#runTaskAsynchronously},
 * whose backing pool is effectively unbounded. A script that forks an async
 * section from a high-frequency event (e.g. {@code on death} at a mob farm)
 * could therefore spawn hundreds of "Craft Scheduler Thread"s and saturate the
 * host CPU. All async work now flows through this bounded pool instead, so the
 * thread count is capped regardless of how fast scripts submit work.
 *
 * <p>Script continuations are not CPU-bound work: they spend most of their time
 * blocked in YAML/database/network calls or in {@code async wait}. A plain
 * {@link ThreadPoolExecutor} only starts extra workers once its queue is full,
 * which would park thousands of continuations behind a handful of blocked
 * threads. {@link ScalingQueue} inverts that: while the pool may still grow and
 * every existing worker is busy, the queue refuses the task so the pool starts
 * another worker instead of queueing behind blocked work.
 *
 * <p>When the pool <em>and</em> its queue are both saturated the continuation is
 * run on the calling thread. That applies natural backpressure (the producer
 * slows down) instead of either dropping the continuation or throwing, which
 * would corrupt script state.
 */
public final class TaskExecutor {

    private static final int AVAILABLE_PROCESSORS = Math.max(1, Runtime.getRuntime().availableProcessors());
    private static final int CORE_POOL_SIZE = Math.max(4, Math.min(16, AVAILABLE_PROCESSORS / 2));
    private static final int MAX_POOL_SIZE = Math.max(CORE_POOL_SIZE, Math.min(64, AVAILABLE_PROCESSORS * 2));
    private static final int QUEUE_CAPACITY = 1000;
    private static final long KEEP_ALIVE_SECONDS = 60L;
    private static final Object LOCK = new Object();

    private static volatile ThreadPoolExecutor asyncExecutor;
    private static volatile ScheduledThreadPoolExecutor delayScheduler;
    private static volatile boolean shuttingDown = true;

    private TaskExecutor() {
    }

    public static void start() {
        synchronized (LOCK) {
            shuttingDown = false;
        }
    }

    /** Run {@code runnable} on the bounded async pool as soon as a worker is free. */
    public static void executeAsync(Runnable runnable) {
        executor().execute(Objects.requireNonNull(runnable, "runnable"));
    }

    /**
     * Run {@code runnable} on the bounded async pool after {@code delayMillis}.
     * The delay is tracked by a tiny scheduler; the actual work still executes
     * on the shared bounded pool, so delayed async work is capped too.
     */
    public static void scheduleAsync(long delayMillis, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        if (delayMillis <= 0L) {
            executeAsync(runnable);
            return;
        }
        scheduler().schedule(() -> {
            try {
                executeAsync(runnable);
            } catch (RejectedExecutionException ignored) {
                // Pool was shut down between scheduling and firing; nothing to do.
            } catch (Throwable throwable) {
                // A ScheduledExecutorService swallows failures into its (discarded) Future,
                // so without this a broken hand-off would vanish without a trace.
                report("Failed to dispatch a delayed async script continuation", throwable);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    public static void shutdown() {
        ExecutorService worker;
        ExecutorService scheduler;
        synchronized (LOCK) {
            shuttingDown = true;
            worker = asyncExecutor;
            scheduler = delayScheduler;
            asyncExecutor = null;
            delayScheduler = null;
        }

        shutdownExecutor(scheduler, "delay scheduler");
        shutdownExecutor(worker, "async pool");
    }

    private static void shutdownExecutor(ExecutorService executor, String name) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                dropped(name, executor.shutdownNow());
            }
        } catch (InterruptedException e) {
            dropped(name, executor.shutdownNow());
            Thread.currentThread().interrupt();
        }
    }

    private static void dropped(String name, List<Runnable> pending) {
        if (!pending.isEmpty()) {
            Bukkit.getLogger().warning("[MundoSK-Async] " + pending.size() + " script continuation(s) were"
                + " still pending on the " + name + " at shutdown and did not finish.");
        }
    }

    static void report(String message, Throwable throwable) {
        Bukkit.getLogger().log(Level.SEVERE, "[MundoSK-Async] " + message, throwable);
    }

    private static ThreadPoolExecutor executor() {
        ThreadPoolExecutor executor = asyncExecutor;
        if (isRunning(executor)) {
            return executor;
        }

        synchronized (LOCK) {
            if (shuttingDown) {
                throw new RejectedExecutionException("TaskExecutor is shutting down");
            }
            executor = asyncExecutor;
            if (!isRunning(executor)) {
                executor = createExecutor();
                asyncExecutor = executor;
            }
            return executor;
        }
    }

    private static ScheduledThreadPoolExecutor scheduler() {
        ScheduledThreadPoolExecutor scheduler = delayScheduler;
        if (scheduler != null && !scheduler.isShutdown()) {
            return scheduler;
        }

        synchronized (LOCK) {
            if (shuttingDown) {
                throw new RejectedExecutionException("TaskExecutor is shutting down");
            }
            scheduler = delayScheduler;
            if (scheduler == null || scheduler.isShutdown()) {
                scheduler = createScheduler();
                delayScheduler = scheduler;
            }
            return scheduler;
        }
    }

    private static boolean isRunning(ThreadPoolExecutor executor) {
        return executor != null && !executor.isShutdown() && !executor.isTerminated();
    }

    private static ThreadPoolExecutor createExecutor() {
        ScalingQueue queue = new ScalingQueue(QUEUE_CAPACITY);
        ThreadPoolExecutor executor = new AsyncPool(
            CORE_POOL_SIZE,
            MAX_POOL_SIZE,
            KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
            queue,
            new CustomThreadFactory("MundoSK-Async"),
            new Backpressure()
        );
        executor.allowCoreThreadTimeOut(true);
        queue.setExecutor(executor);
        return executor;
    }

    private static ScheduledThreadPoolExecutor createScheduler() {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(
            1, new CustomThreadFactory("MundoSK-Async-Scheduler"));
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return scheduler;
    }

    /**
     * The async pool, with the task failures that {@link ThreadPoolExecutor#execute} would
     * otherwise discard routed to the server log.
     */
    private static final class AsyncPool extends ThreadPoolExecutor {

        AsyncPool(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
                  LinkedBlockingQueue<Runnable> workQueue, ThreadFactory threadFactory,
                  RejectedExecutionHandler handler) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        }

        @Override
        protected void afterExecute(Runnable runnable, Throwable throwable) {
            super.afterExecute(runnable, throwable);
            if (throwable != null) {
                report("An async script continuation ended with an error", throwable);
            }
        }
    }

    /**
     * A bounded queue that refuses tasks while the pool is still allowed to grow and all of its
     * workers are busy, which is what makes {@link ThreadPoolExecutor} add a worker instead of
     * queueing the task behind work that is very likely blocked in a script wait.
     */
    private static final class ScalingQueue extends LinkedBlockingQueue<Runnable> {

        private static final long serialVersionUID = 1L;

        private transient volatile ThreadPoolExecutor executor;

        ScalingQueue(int capacity) {
            super(capacity);
        }

        void setExecutor(ThreadPoolExecutor executor) {
            this.executor = executor;
        }

        @Override
        public boolean offer(Runnable runnable) {
            ThreadPoolExecutor executor = this.executor;
            if (executor != null) {
                int poolSize = executor.getPoolSize();
                if (poolSize < executor.getMaximumPoolSize() && executor.getActiveCount() >= poolSize) {
                    return false; // let the pool start another worker
                }
            }
            return super.offer(runnable);
        }

        /** Enqueues without the growth check, for tasks the pool failed to hand to a worker. */
        boolean enqueue(Runnable runnable) {
            return super.offer(runnable);
        }
    }

    /**
     * Handles what {@link ScalingQueue} refused but the pool could not take either: normally that
     * means growing lost a race, in which case the task simply belongs in the queue. Only when the
     * queue is genuinely full is the task run on the calling thread, slowing the producer down.
     */
    private static final class Backpressure implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
            if (executor.isShutdown()) {
                throw new RejectedExecutionException("MundoSK-Async pool is shutting down");
            }
            if (executor.getQueue() instanceof ScalingQueue queue && queue.enqueue(runnable)) {
                return;
            }
            runnable.run();
        }
    }

    private static final class CustomThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger count = new AtomicInteger();

        CustomThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, namePrefix + "-Thread-" + count.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((failed, throwable) ->
                report("Uncaught error on " + failed.getName(), throwable));
            return thread;
        }
    }
}
