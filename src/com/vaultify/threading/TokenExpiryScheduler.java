package com.vaultify.threading;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Day 1 skeleton for scheduling token expiry/cleanup tasks.
 */
public class TokenExpiryScheduler {

    /**
     * Schedule a periodic cleanup task using the ThreadManager scheduler.
     */
    public static ScheduledFuture<?> scheduleTokenCleanup(Runnable task, long initialDelay, long period,
            TimeUnit unit) {
        return ThreadManager.scheduleAtFixedRate(task, initialDelay, period, unit);
    }
}
