package com.vaultify.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.UUID;

import com.vaultify.crypto.AESEngine;
import com.vaultify.crypto.HashUtil;
import com.vaultify.crypto.RSAEngine;
import com.vaultify.models.CredentialMetadata;

/**
 * CredentialFileManager - THE CORE OF VAULTIFY
 * 
 * Handles:
 * 1. File encryption with AES-256-GCM
 * 2. AES key wrapping with RSA-OAEP
 * 3. Secure storage of encrypted files
 * 4. Decryption and retrieval
 */
public class CredentialFileManager {
    private static final String ENCRYPTED_DIR = Config.get("vault.storage", "./vault_data/credentials/");

    /**
     * Encrypt and store a file securely.
     * 
     * @param plainFile     Path to the plaintext file to encrypt
     * @param userPublicKey User's RSA public key (for wrapping AES key)
     * @param userId        Owner user ID
     * @return CredentialMetadata containing encryption details
     */
    public static CredentialMetadata encryptAndStore(Path plainFile, PublicKey userPublicKey, long userId)
            throws Exception {
        // Ensure storage directory exists
        Files.createDirectories(Paths.get(ENCRYPTED_DIR));

        // Read plaintext file
        byte[] plaintext = Files.readAllBytes(plainFile);

        // Generate unique credential ID
        String credentialId = UUID.randomUUID().toString();

        // Calculate hash of original file
        String originalHash = HashUtil.sha256(new String(plaintext));

        // Generate AES key and IV
        byte[] aesKey = AESEngine.generateKey();
        byte[] iv = AESEngine.generateIv();

        // Encrypt file with AES-GCM (using ThreadManager for async execution if large,
        // but here we block for result)
        // Using EncryptionTask to utilize the threading layer
        com.vaultify.threading.EncryptionTask task = new com.vaultify.threading.EncryptionTask(plaintext, aesKey, iv);
        byte[] ciphertext = com.vaultify.threading.ThreadManager.submit(task).get();

        // Wrap AES key with user's RSA public key
        byte[] wrappedKey = RSAEngine.encryptWithKey(aesKey, userPublicKey);

        // Save encrypted file to disk
        Path encryptedFilePath = Paths.get(ENCRYPTED_DIR, credentialId + ".bin");
        Files.write(encryptedFilePath, ciphertext);

        // Create metadata
        CredentialMetadata meta = new CredentialMetadata();
        meta.credentialIdString = credentialId;
        // Credential hash must bind to encrypted bytes for integrity (not just ID)
        meta.credentialHash = HashUtil.sha256(ciphertext);
        meta.filename = plainFile.getFileName().toString();
        meta.dataHash = originalHash;
        meta.fileSize = plaintext.length;
        meta.timestamp = System.currentTimeMillis();
        meta.encryptedKeyBase64 = Base64.getEncoder().encodeToString(wrappedKey);
        meta.ivBase64 = Base64.getEncoder().encodeToString(iv);
        meta.userId = userId;

        return meta;
    }

    /**
     * Decrypt and retrieve a stored file.
     * 
     * @param credentialId       UUID of the credential
     * @param encryptedKeyBase64 Base64-encoded wrapped AES key
     * @param ivBase64           Base64-encoded IV
     * @param userPrivateKey     User's RSA private key (for unwrapping AES key)
     * @return Decrypted file contents
     */
    public static byte[] decryptAndRetrieve(String credentialId, String encryptedKeyBase64,
            String ivBase64, PrivateKey userPrivateKey) throws Exception {
        // Load encrypted file
        Path encryptedFilePath = Paths.get(ENCRYPTED_DIR, credentialId + ".bin");
        if (!Files.exists(encryptedFilePath)) {
            throw new IOException("Encrypted file not found: " + credentialId);
        }
        byte[] ciphertext = Files.readAllBytes(encryptedFilePath);

        // Unwrap AES key using user's private key
        byte[] wrappedKey = Base64.getDecoder().decode(encryptedKeyBase64);
        byte[] aesKey = RSAEngine.decryptWithKey(wrappedKey, userPrivateKey);

        // Decode IV
        byte[] iv = Base64.getDecoder().decode(ivBase64);

        // Decrypt file
        byte[] plaintext = AESEngine.decryptWithParams(ciphertext, aesKey, iv);

        return plaintext;
    }

    /**
     * Delete a stored encrypted file.
     * 
     * @param credentialId UUID of the credential to delete
     */
    public static void deleteEncryptedFile(String credentialId) throws IOException {
        Path encryptedFilePath = Paths.get(ENCRYPTED_DIR, credentialId + ".bin");
        if (Files.exists(encryptedFilePath)) {
            Files.delete(encryptedFilePath);
        }
    }

    /**
     * Check if an encrypted file exists.
     */
    public static boolean exists(String credentialId) {
        Path encryptedFilePath = Paths.get(ENCRYPTED_DIR, credentialId + ".bin");
        return Files.exists(encryptedFilePath);
    }
}
