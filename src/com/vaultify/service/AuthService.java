package com.vaultify.service;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import com.vaultify.crypto.AESEngine;
import com.vaultify.crypto.HashUtil;
import com.vaultify.crypto.RSAEngine;
import com.vaultify.models.User;
import com.vaultify.repository.RepositoryFactory;
import com.vaultify.repository.UserRepository;
import com.vaultify.threading.ThreadManager;

/**
 * AuthService handles authentication operations: login, registration, session
 * management.
 * Uses DUAL storage: File-based for backup/portability + JDBC for querying.
 * Uses crypto for password hashing, RSA key generation, and AES private key
 * encryption.
 */
public class AuthService {
    private final UserRepository userRepository; // dual/selected repository
    private final LedgerService ledgerService;

    // Current session
    private User currentUser;
    private PrivateKey currentUserPrivateKey;

    public AuthService() {
        // Acquire repository from factory for configured storage.mode
        this.userRepository = RepositoryFactory.get().userRepository();
        this.ledgerService = new LedgerService();
    }

    /**
     * Register a new user with username and password.
     * Generates RSA key pair, encrypts private key with password-derived AES key.
     * 
     * @param username Username for the new user
     * @param password Plain text password
     * @return User object if successful, null if username exists
     */
    public User register(String username, String password) {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            throw new ServiceException("Username and password cannot be empty");
        }

        // Check if user already exists (check both storages)
        if (userRepository.findByUsername(username) != null) {
            throw new ServiceException("Username already exists");
        }

        try {
            // Create new user
            User user = new User();
            user.setUsername(username);

            // Hash password with SHA-256
            String passwordHash = HashUtil.sha256(password);
            user.setPasswordHash(passwordHash);

            // Generate RSA key pair (2048-bit)
            KeyPair keyPair = RSAEngine.generateKeyPair(2048);

            // Store public key as Base64-encoded X.509 format
            String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            user.setPublicKey(publicKeyBase64);

            // Encrypt private key with AES using password-derived key
            // Use first 32 bytes of password hash as AES key
            byte[] aesKey = Base64.getDecoder().decode(Base64.getEncoder().encodeToString(
                    HashUtil.sha256(password).substring(0, 32).getBytes()));
            if (aesKey.length < 32) {
                // Ensure 32 bytes for AES-256
                aesKey = HashUtil.sha256(password).substring(0, 32).getBytes();
                if (aesKey.length < 32) {
                    byte[] padded = new byte[32];
                    System.arraycopy(aesKey, 0, padded, 0, aesKey.length);
                    aesKey = padded;
                }
            }

            byte[] iv = AESEngine.generateIv();
            byte[] privateKeyBytes = keyPair.getPrivate().getEncoded();
            byte[] encryptedPrivateKey = AESEngine.encryptWithParams(privateKeyBytes, aesKey, iv);

            // Store as Base64: IV || encrypted_private_key
            byte[] combined = new byte[iv.length + encryptedPrivateKey.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedPrivateKey, 0, combined, iv.length, encryptedPrivateKey.length);
            String encryptedPrivateKeyBase64 = Base64.getEncoder().encodeToString(combined);
            user.setPrivateKeyEncrypted(encryptedPrivateKeyBase64);

            // Persist key artifacts to filesystem for features that expect PEM presence.
            // We do NOT write the raw unencrypted private key; instead we store the
            // encrypted blob.
            try {
                java.nio.file.Path keysDir = java.nio.file.Paths.get("vault_data", "keys");
                java.nio.file.Files.createDirectories(keysDir);

                // Public key PEM encoding (RFC 7468 style, 64-char lines)
                String pubPem = "-----BEGIN PUBLIC KEY-----\n"
                        + java.util.Base64.getMimeEncoder(64, "\n".getBytes())
                                .encodeToString(keyPair.getPublic().getEncoded())
                        + "\n-----END PUBLIC KEY-----\n";
                java.nio.file.Files.writeString(keysDir.resolve(username + "_public.pem"), pubPem);

                // Encrypted private key blob (IV||ciphertext base64) – not a standard PEM,
                // store with .enc
                java.nio.file.Files.writeString(keysDir.resolve(username + "_private.enc"), encryptedPrivateKeyBase64);
            } catch (Exception ioEx) {
                System.err.println("[Warning] Failed to write key files: " + ioEx.getMessage());
            }

            // Persist via repository abstraction (handles dual strategy internally)
            userRepository.save(user);

            // Log registration to ledger
            String dataHash = HashUtil.sha256("REGISTER:" + username + ":" + publicKeyBase64);
            ThreadManager
                    .runAsync(() -> ledgerService.appendBlock(user.getId(), username, "USER_REGISTERED", dataHash));

            return user;
        } catch (Exception e) {
            throw new ServiceException("Failed to register user: " + e.getMessage(), e);
        }
    }

    /**
     * Login with username and password.
     * Verifies password hash and decrypts private key.
     * 
     * @param username Username
     * @param password Plain text password
     * @return true if login successful, false otherwise
     */
    public boolean login(String username, String password) {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return false;
        }

        try {
            // Unified repository lookup (dual strategy inside repository)
            User user = userRepository.findByUsername(username);

            if (user == null) {
                return false;
            }

            // Verify password
            String passwordHash = HashUtil.sha256(password);
            if (!passwordHash.equals(user.getPasswordHash())) {
                return false;
            }

            // Decrypt private key
            byte[] aesKey = HashUtil.sha256(password).substring(0, 32).getBytes();
            if (aesKey.length < 32) {
                byte[] padded = new byte[32];
                System.arraycopy(aesKey, 0, padded, 0, aesKey.length);
                aesKey = padded;
            }

            byte[] combined = Base64.getDecoder().decode(user.getPrivateKeyEncrypted());
            byte[] iv = new byte[AESEngine.GCM_IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, AESEngine.GCM_IV_BYTES);
            byte[] encryptedPrivateKey = new byte[combined.length - AESEngine.GCM_IV_BYTES];
            System.arraycopy(combined, AESEngine.GCM_IV_BYTES, encryptedPrivateKey, 0, encryptedPrivateKey.length);

            byte[] privateKeyBytes = AESEngine.decryptWithParams(encryptedPrivateKey, aesKey, iv);

            // Reconstruct private key
            java.security.spec.PKCS8EncodedKeySpec keySpec = new java.security.spec.PKCS8EncodedKeySpec(
                    privateKeyBytes);
            java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

            // Set session
            this.currentUser = user;
            this.currentUserPrivateKey = privateKey;

            // Log successful login to ledger
            final long userId = user.getId();
            final String usernameForLog = username;
            String dataHash = HashUtil.sha256("LOGIN:" + username + ":" + System.currentTimeMillis());
            ThreadManager.runAsync(() -> ledgerService.appendBlock(userId, usernameForLog, "USER_LOGIN", dataHash));

            return true;
        } catch (Exception e) {
            // Log error but return false for security
            System.err.println("Login failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Logout current user.
     */
    public void logout() {
        this.currentUser = null;
        this.currentUserPrivateKey = null;
    }

    /**
     * Check if a user is currently logged in.
     */
    public boolean isLoggedIn() {
        return currentUser != null;
    }

    /**
     * Get current logged-in user.
     */
    public User getCurrentUser() {
        return currentUser;
    }

    /**
     * Get current user's private key (decrypted).
     */
    public PrivateKey getCurrentUserPrivateKey() {
        return currentUserPrivateKey;
    }

    /**
     * Get current user's public key.
     */
    public PublicKey getCurrentUserPublicKey() throws Exception {
        if (currentUser == null) {
            return null;
        }
        byte[] publicKeyBytes = Base64.getDecoder().decode(currentUser.getPublicKey());
        java.security.spec.X509EncodedKeySpec keySpec = new java.security.spec.X509EncodedKeySpec(publicKeyBytes);
        java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }

    /**
     * Get a user's public key by username.
     * Used for encrypting credentials for that user.
     * 
     * @param username Username to get public key for
     * @return PublicKey or null if user not found
     */
    public PublicKey getUserPublicKey(String username) {
        try {
            // Try current user first
            if (currentUser != null && currentUser.getUsername().equals(username)) {
                return getCurrentUserPublicKey();
            }

            // Load user from storage
            User user = userRepository.findByUsername(username);

            if (user == null) {
                return null;
            }

            byte[] publicKeyBytes = Base64.getDecoder().decode(user.getPublicKey());
            java.security.spec.X509EncodedKeySpec keySpec = new java.security.spec.X509EncodedKeySpec(publicKeyBytes);
            java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(keySpec);

        } catch (Exception e) {
            System.err.println("Failed to load public key for user " + username + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Get a user's private key by username.
     * SECURITY: This should only return the current logged-in user's private key.
     * 
     * @param username Username to get private key for
     * @return PrivateKey or null if not current user or not found
     */
    public PrivateKey getUserPrivateKey(String username) {
        // Security check: only return private key for current logged-in user
        if (currentUser == null || !currentUser.getUsername().equals(username)) {
            System.err.println("Security violation: attempted to access private key for user " + username);
            return null;
        }
        return currentUserPrivateKey;
    }
}
