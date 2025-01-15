package gg.projects.mundoskasync;

import java.util.concurrent.*;

public class TaskExecutor {
    private static final ExecutorService asyncExecutor = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors() * 2,
            60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100),
            new CustomThreadFactory("MundoSK-Async")
    );

    public static void executeAsync(Runnable runnable) {
        asyncExecutor.execute(runnable);
    }

    public static void shutdown() {
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class CustomThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private int count = 0;

        CustomThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, namePrefix + "-Thread-" + count++);
        }
    }
}