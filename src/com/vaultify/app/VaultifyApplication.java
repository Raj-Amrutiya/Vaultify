package com.vaultify.app;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.vaultify.cli.CommandRouter;
import com.vaultify.threading.ActivityLogger;
import com.vaultify.threading.ThreadManager;
import com.vaultify.threading.TokenCleanupTask;

public class VaultifyApplication {
    private static final String CURRENT_VERSION = "0.0.1-beta";
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/HetMistri/Vaultify/releases/latest";

    public static void main(String[] args) {
        System.out.println("Vaultify CLI v0.1 Beta starting...");

        // Start background activity logger
        ActivityLogger logger = new ActivityLogger();
        ThreadManager.runAsync(logger);
        System.out.println("Activity logger started");

        // Schedule token expiry cleanup (runs every hour)
        ThreadManager.scheduleAtFixedRate(
                new TokenCleanupTask(),
                0, // Initial delay
                3600, // Period: 1 hour
                TimeUnit.SECONDS);
        System.out.println("Token cleanup scheduler started (runs hourly)");
        System.out.println("===============================================");
        System.out.println("  Welcome to Vaultify v0.1-beta");
        System.out.println("  Secure Credential Vault System");
        System.out.println("===============================================");
        maybeNotifyUpdate();
        System.out.println("Type 'help' for commands or 'register' to start");
        System.out.println();

        // Add shutdown hook for graceful cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down Vaultify...");
            logger.shutdown();
            ThreadManager.shutdown();
            System.out.println("Vaultify shutdown complete");
        }));

        // Start CLI
        new CommandRouter().start();
    }

    private static void maybeNotifyUpdate() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LATEST_RELEASE_API))
                    .timeout(Duration.ofSeconds(3))
                    .header("Accept", "application/vnd.github+json")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return; // quietly ignore on failure
            }

            String body = response.body();
            String tag = extractTagName(body);
            if (tag != null && !tag.equalsIgnoreCase(CURRENT_VERSION)) {
                System.out.println("⚠️  A newer Vaultify version is available: " + tag);
                System.out.println("Run update.bat (Windows) or update.sh (Mac/Linux) to update.");
                System.out.println();
            }
        } catch (Exception ignored) {
            // Do not block startup on update check failures
        }
    }

    private static String extractTagName(String json) {
        if (json == null)
            return null;
        String key = "\"tag_name\":";
        int idx = json.indexOf(key);
        if (idx == -1)
            return null;
        int start = json.indexOf('"', idx + key.length());
        if (start == -1)
            return null;
        int end = json.indexOf('"', start + 1);
        if (end == -1)
            return null;
        return json.substring(start + 1, end);
    }
}
