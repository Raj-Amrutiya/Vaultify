package com.vaultify.verifier;

/**
 * Certificate structure for 4-layer verification flow.
 * Stores SHA256(token) instead of raw token for confidentiality.
 */
public class Certificate {
    // Core identity (ONLY tokenHash stored, not raw token)
    public String tokenHash; // SHA256(token) - ensures token confidentiality
    public long credentialId; // referenced credential
    public long issuerUserId; // user who issued token

    // Security bindings
    public String credentialHash; // sha256(encryptedFileBytes) from metadata
    public String issuerPublicKeyPem; // issuer's RSA public key (PEM format)
    public String payloadHash; // SHA256(tokenHash + credentialId + issuerPublicKey + expiry)
    public String signatureBase64; // RSA signature over payloadHash

    // Timestamps
    public long expiryEpochMs; // absolute expiry time (ms)
    public long createdAtMs; // creation timestamp

    // Ledger anchoring
    public String ledgerBlockHash; // hash of ledger block recording issuance
}
