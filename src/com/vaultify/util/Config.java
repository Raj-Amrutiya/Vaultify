package com.vaultify.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Configuration loader with layered approach:
 * 1. Loads .env file (for sensitive credentials like DB passwords, API keys)
 * 2. Loads config.properties from classpath (for non-sensitive defaults)
 * 3. Allows environment variable overrides
 * 
 * Priority: System.getenv() > .env > config.properties
 */
public class Config {
    private static final Properties props = new Properties();
    private static final Dotenv dotenv;

    static {
        // Load .env file from multiple possible locations
        dotenv = loadDotenv();

        // Load config.properties for non-sensitive defaults
        try (InputStream is = Config.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (is == null) {
                throw new RuntimeException("config.properties missing in resources/");
            }
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.properties", e);
        }
    }

    /**
     * Attempts to load .env from multiple locations with fallback to jar resources:
     * 1. Directory specified by DOTENV_PATH system property
     * 2. Current working directory
     * 3. Directory containing the jar file
     * 4. User's home directory
     * 5. Bundled in jar resources (packaged with application) - FALLBACK
     */
    private static Dotenv loadDotenv() {
        // Check if custom path is specified via system property
        String customPath = System.getProperty("dotenv.path");
        if (customPath != null) {
            try {
                return Dotenv.configure()
                        .directory(customPath)
                        .ignoreIfMissing()
                        .load();
            } catch (Exception e) {
                System.err.println("Warning: Could not load .env from custom path: " + customPath);
            }
        }

        // Try current working directory first
        try {
            Dotenv env = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();
            if (env.get("DB_URL") != null || env.get("LEDGER_API_URL") != null) {
                System.out.println("✓ Loaded .env from current directory");
                return env;
            }
        } catch (Exception e) {
            // Silent fail, try next location
        }

        // Try jar directory (where the application is executed from)
        try {
            String jarPath = Config.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();
            java.io.File jarFile = new java.io.File(jarPath);
            String jarDir = jarFile.getParent();
            
            if (jarDir != null) {
                Dotenv env = Dotenv.configure()
                        .directory(jarDir)
                        .ignoreIfMissing()
                        .load();
                if (env.get("DB_URL") != null || env.get("LEDGER_API_URL") != null) {
                    System.out.println("✓ Loaded .env from jar directory: " + jarDir);
                    return env;
                }
            }
        } catch (Exception e) {
            // Silent fail, try next location
        }

        // Try user home directory as fallback
        try {
            String userHome = System.getProperty("user.home");
            Dotenv env = Dotenv.configure()
                    .directory(userHome)
                    .filename(".vaultify.env")
                    .ignoreIfMissing()
                    .load();
            if (env.get("DB_URL") != null || env.get("LEDGER_API_URL") != null) {
                System.out.println("✓ Loaded .vaultify.env from home directory: " + userHome);
                return env;
            }
        } catch (Exception e) {
            // Silent fail
        }

        // FALLBACK: Load bundled .env from jar resources
        try {
            InputStream is = Config.class.getClassLoader().getResourceAsStream(".env");
            if (is != null) {
                // Create a temporary file from the resource stream
                java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("vaultify-", ".env");
                java.nio.file.Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                
                Dotenv env = Dotenv.configure()
                        .filename(tempFile.getFileName().toString())
                        .directory(tempFile.getParent().toString())
                        .ignoreIfMissing()
                        .load();
                
                System.out.println("✓ Loaded bundled .env from jar resources");
                return env;
            }
        } catch (Exception e) {
            // If bundled .env doesn't exist, that's okay
        }

        System.out.println("ℹ Using config.properties defaults and system environment variables");
        System.out.println("  For custom config, place .env file in the same directory as the jar");
        // Return empty dotenv (will fall back to config.properties)
        return Dotenv.configure()
                .ignoreIfMissing()
                .load();
    }

    public static String get(String key) {
        // Convert key format: db.url -> DB_URL
        String envKey = key.toUpperCase().replace('.', '_');

        // Priority 1: System environment variables (highest priority)
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }

        // Priority 2: .env file
        String dotenvValue = dotenv.get(envKey);
        if (dotenvValue != null && !dotenvValue.isEmpty()) {
            return dotenvValue;
        }

        // Priority 3: config.properties (fallback)
        return props.getProperty(key);
    }

    public static String get(String key, String defaultValue) {
        String value = get(key);
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }

    public static int getInt(String key, int defaultValue) {
        try {
            String value = get(key);
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    public static boolean isDevMode() {
        return getBoolean("dev.mode", false);
    }
}
