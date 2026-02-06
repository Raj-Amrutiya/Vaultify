package com.vaultify.threading;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Day 1 Thread layer skeleton.
 * Centralizes executors for async and scheduled work.
 */
public class ThreadManager {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(2);

    /**
     * Submit a Runnable for asynchronous execution.
     */
    public static void runAsync(Runnable task) {
        EXECUTOR.submit(task);
    }

    /**
     * Submit a value-returning task.
     */
    public static <T> Future<T> submit(Callable<T> task) {
        return EXECUTOR.submit(task);
    }

    /**
     * Schedule a periodic task.
     */
    public static ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return SCHEDULER.scheduleAtFixedRate(task, initialDelay, period, unit);
    }

    /**
     * Gracefully shutdown all executors.
     */
    public static void shutdown() {
        EXECUTOR.shutdown();
        SCHEDULER.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                EXECUTOR.shutdownNow();
            }
            if (!SCHEDULER.awaitTermination(5, TimeUnit.SECONDS)) {
                SCHEDULER.shutdownNow();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            EXECUTOR.shutdownNow();
            SCHEDULER.shutdownNow();
        }
    }
}
