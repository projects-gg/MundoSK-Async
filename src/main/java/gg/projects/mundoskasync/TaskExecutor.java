package gg.projects.mundoskasync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class TaskExecutor {
    private static final ExecutorService asyncExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(), new CustomThreadFactory("MundoSK-Async")
    );

    public static void executeAsync(Runnable runnable) {
        asyncExecutor.execute(() -> {
            try {
                runnable.run();
            } catch (Exception e) {
                MundoSKAsync.getInstance().getLogger().severe("Error in async task: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public static void shutdown() {
        asyncExecutor.shutdownNow();
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
