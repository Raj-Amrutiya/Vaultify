package com.vaultify.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * PathValidator - Security checks for file operations
 * 
 * Validates:
 * - File existence
 * - Size limits
 * - Extension blacklist
 * - Path traversal prevention
 */
public class PathValidator {
    private static final long MAX_FILE_SIZE;
    private static final Path BASE_PATH;
    private static final Set<String> BLACKLISTED_EXTENSIONS;

    static {
        // Load from config
        MAX_FILE_SIZE = Long.parseLong(Config.get("vault.maxFileSize", "10485760")); // 10MB default
        BASE_PATH = Paths.get(Config.get("vault.basePath", "./vault_data")).toAbsolutePath().normalize();

        String blacklistStr = Config.get("vault.blacklist.extensions",
                ".mp4,.avi,.mov,.mkv,.flv,.wmv,.webm,.gif,.m4v,.mpg,.mpeg,.3gp,.ogv");
        BLACKLISTED_EXTENSIONS = new HashSet<>(Arrays.asList(blacklistStr.toLowerCase().split(",")));
    }

    public static class ValidationResult {
        public final boolean valid;
        public final String message;
        public final Path normalizedPath;

        public ValidationResult(boolean valid, String message, Path normalizedPath) {
            this.valid = valid;
            this.message = message;
            this.normalizedPath = normalizedPath;
        }
    }

    /**
     * Validate a file path for security and constraints.
     */
    public static ValidationResult validateFilePath(String pathStr) {
        try {
            Path path = Paths.get(pathStr).toAbsolutePath().normalize();

            // Check existence
            if (!Files.exists(path)) {
                return new ValidationResult(false, "File does not exist: " + pathStr, null);
            }

            // Check it's a regular file
            if (!Files.isRegularFile(path)) {
                return new ValidationResult(false, "Path is not a regular file: " + pathStr, null);
            }

            // Check readable
            if (!Files.isReadable(path)) {
                return new ValidationResult(false, "File is not readable: " + pathStr, null);
            }

            // Check size
            long size = Files.size(path);
            if (size > MAX_FILE_SIZE) {
                return new ValidationResult(false,
                        String.format("File too large: %d bytes (max: %d bytes)", size, MAX_FILE_SIZE), null);
            }

            // Check extension blacklist
            String filename = path.getFileName().toString().toLowerCase();
            for (String ext : BLACKLISTED_EXTENSIONS) {
                if (filename.endsWith(ext)) {
                    return new ValidationResult(false,
                            "File type not allowed: " + ext + " (blacklisted)", null);
                }
            }

            return new ValidationResult(true, "Valid", path);

        } catch (IOException e) {
            return new ValidationResult(false, "Error reading file: " + e.getMessage(), null);
        } catch (Exception e) {
            return new ValidationResult(false, "Invalid path: " + e.getMessage(), null);
        }
    }

    /**
     * Validate that a path doesn't escape the base directory (path traversal
     * prevention).
     * Used for operations that write to disk.
     */
    public static boolean isWithinBasePath(Path path) {
        try {
            Path normalized = path.toAbsolutePath().normalize();
            return normalized.startsWith(BASE_PATH);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the maximum allowed file size.
     */
    public static long getMaxFileSize() {
        return MAX_FILE_SIZE;
    }

    /**
     * Get a human-readable size string.
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
