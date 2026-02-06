package com.vaultify.verifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class CertificateParser {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Parse certificate JSON file into Certificate object.
     * 
     * @param certPath filesystem path to cert JSON
     */
    public static Certificate parse(Path certPath) throws Exception {
        byte[] bytes = Files.readAllBytes(certPath);
        String json = new String(bytes, StandardCharsets.UTF_8);
        Certificate cert = GSON.fromJson(json, Certificate.class);
        if (cert == null)
            throw new IllegalArgumentException("Invalid certificate JSON: " + certPath);
        return cert;
    }

    public static void save(Certificate cert, Path outPath) throws Exception {
        Files.createDirectories(outPath.getParent());
        String json = GSON.toJson(cert);
        Files.write(outPath, json.getBytes(StandardCharsets.UTF_8));
    }
}
