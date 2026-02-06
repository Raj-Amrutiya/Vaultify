package com.vaultify.repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Central factory for creating repository instances based on configuration.
 * Reads storage.mode from config.properties (classpath root).
 * Values:
 * - dual (default): Dual*Repository implementations
 * - jdbc: Postgres*Repository implementations only
 * - file: File*Repository implementations only
 */
public final class RepositoryFactory {
    private static final String CONFIG_FILE = "config.properties";
    private static final String MODE_KEY = "storage.mode";
    private static final RepositoryFactory INSTANCE = new RepositoryFactory();

    private final String storageMode;

    private RepositoryFactory() {
        this.storageMode = loadMode();
        System.out.println("[RepositoryFactory] storage.mode=" + storageMode);
    }

    public static RepositoryFactory get() {
        return INSTANCE;
    }

    private String loadMode() {
        Properties props = new Properties();
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (in != null)
                props.load(in);
        } catch (IOException ignored) {
        }
        String mode = props.getProperty(MODE_KEY, "dual").trim().toLowerCase();
        // Force JDBC mode for production integrity when config requests jdbc
        if ("jdbc".equals(mode)) {
            return "jdbc";
        }
        // If mode is invalid or unspecified, default to jdbc (no file-based
        // persistence)
        if (!"file".equals(mode) && !"dual".equals(mode)) {
            return "jdbc";
        }
        return mode;
    }

    // User Repository
    public UserRepository userRepository() {
        // Enforce JDBC-only repositories
        return new PostgresUserRepository();
    }

    // Credential Repository
    public CredentialRepository credentialRepository() {
        // Enforce JDBC-only repositories
        return new PostgresCredentialRepository();
    }

    // Token Repository
    public TokenRepository tokenRepository() {
        // Enforce JDBC-only repositories
        return new PostgresTokenRepository();
    }
}
