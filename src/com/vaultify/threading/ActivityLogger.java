package com.vaultify.threading;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Background activity logger for async log writing.
 * Processes log entries from a queue without blocking main operations.
 */
public class ActivityLogger implements Runnable {
    private static final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();
    private static final Path LOG_FILE = Paths.get("vault_data/activity.log");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private volatile boolean running = true;

    public static void log(String action, String details) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String logEntry = String.format("[%s] %s: %s", timestamp, action, details);
        logQueue.offer(logEntry);
    }

    @Override
    public void run() {
        try {
            Files.createDirectories(LOG_FILE.getParent());
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE.toFile(), true))) {
                while (running || !logQueue.isEmpty()) {
                    String logEntry = logQueue.poll();
                    if (logEntry != null) {
                        writer.write(logEntry);
                        writer.newLine();
                        writer.flush();
                    } else {
                        // Use blocking poll with timeout instead of sleep
                        try {
                            logQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Activity logger error: " + e.getMessage());
        }
    }

    public void shutdown() {
        running = false;
    }
}
