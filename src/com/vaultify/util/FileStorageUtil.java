package com.vaultify.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class FileStorageUtil {

    /**
     * Implements saveFile skeleton [cite: 245]
     * (Corrected signature from byte to byte[])
     */
    public static void saveFile(byte[] data, String path) {
        try {
            // This will create the file if it doesn't exist, or overwrite it if it does.
            Files.write(Paths.get(path), data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Error saving file to " + path, e);
        }
    }

    /**
     * Implements readFile skeleton [cite: 246]
     */
    public static byte[] readFile(String path) {
        try {
            return Files.readAllBytes(Paths.get(path));
        } catch (IOException e) {
            throw new RuntimeException("Error reading file from " + path, e);
        }
    }
}