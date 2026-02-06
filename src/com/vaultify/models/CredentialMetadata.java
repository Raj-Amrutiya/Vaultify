package com.vaultify.models;

public class CredentialMetadata {
    public int id;
    public CredentialType type;
    public String filename;

    // Additional fields for file-based credential system
    public String credentialIdString; // UUID for this credential
    public String credentialHash; // SHA-256 hash of credentialId (for ledger/verification)
    public String dataHash; // SHA-256 hash of original file
    public long fileSize; // Original file size in bytes
    public long timestamp; // Creation timestamp
    public String encryptedKeyBase64; // AES key encrypted with RSA, base64 encoded
    public String ivBase64; // AES IV, base64 encoded
    public long userId; // Owner user ID
}
