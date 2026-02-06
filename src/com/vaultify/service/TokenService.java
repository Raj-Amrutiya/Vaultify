package com.vaultify.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.Signature;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.List;

import com.vaultify.client.LedgerClient;
import com.vaultify.crypto.HashUtil;
import com.vaultify.models.CredentialMetadata;
import com.vaultify.models.LedgerBlock;
import com.vaultify.models.Token;
import com.vaultify.repository.RepositoryFactory;
import com.vaultify.repository.TokenRepository;
import com.vaultify.util.TokenUtil;
import com.vaultify.verifier.Certificate;
import com.vaultify.verifier.CertificateParser;

/**
 * TokenService - Token generation, validation, and certificate creation with
 * persistent storage.
 * 
 * Handles:
 * - Generating unique share tokens with DB persistence
 * - Creating signed certificates for tokens
 * - Validating token format and revocation status
 * - Revoking tokens with persistence
 * - Ledger integration for all token operations
 */
public class TokenService {
    private final LedgerService ledgerService;
    private final TokenRepository tokenRepository; // dual repository abstraction

    public TokenService() {
        this.ledgerService = new LedgerService();
        this.tokenRepository = RepositoryFactory.get().tokenRepository();
    }

    /**
     * Generate and persist a share token.
     * 
     * @param issuerUserId User generating the token
     * @param credentialId Credential being shared
     * @param expiryHours  Token validity in hours
     * @return Token object
     */
    public Token generateAndSaveToken(long issuerUserId, long credentialId, int expiryHours) {
        if (expiryHours <= 0) {
            throw new ServiceException("Expiry hours must be positive");
        }
        String tokenString = TokenUtil.generateToken();
        long expiry = System.currentTimeMillis() + (expiryHours * 3600L * 1000L);

        Token token = new Token();
        token.setToken(tokenString);
        token.setIssuerUserId(issuerUserId);
        token.setCredentialId(credentialId);
        token.setExpiry(new Timestamp(expiry));
        token.setRevoked(false);

        tokenRepository.save(token);

        // Append to ledger using tokenHash (not raw token)
        String tokenHash = HashUtil.sha256(tokenString);
        String dataHash = HashUtil.sha256(tokenHash + ":" + credentialId);
        // Note: Username lookup would require DAO injection - using ID for now
        ledgerService.appendBlock(issuerUserId, "user_" + issuerUserId, "GENERATE_TOKEN", dataHash);

        return token;
    }

    public Token generateToken(long issuerUserId, long credentialId, int expiryHours) {
        String tokenString = TokenUtil.generateToken();
        long expiry = System.currentTimeMillis() + (expiryHours * 3600L * 1000L);
        Token token = new Token();
        token.setToken(tokenString);
        token.setIssuerUserId(issuerUserId);
        token.setCredentialId(credentialId);
        token.setExpiry(new Timestamp(expiry));
        token.setRevoked(false);
        return token;
    }

    public void persistToken(Token token) {
        tokenRepository.save(token);
    }

    public Certificate createCertificate(Token token, CredentialMetadata meta,
            PrivateKey issuerPrivateKey, Path issuerPublicKeyPath, Path outputPath) throws Exception {
        long now = System.currentTimeMillis();

        // Step A1: Compute tokenHash = SHA256(token)
        String tokenHash = HashUtil.sha256(token.getToken());

        // Step A2: Read issuer public key for inclusion
        String issuerPublicKeyPem = Files.readString(issuerPublicKeyPath);

        // Step A3: Submit to Ledger Server first to get block hash
        String dataHash = HashUtil.sha256(tokenHash + ":" + token.getCredentialId());
        LedgerBlock block = ledgerService.appendBlock(
                token.getIssuerUserId(),
                "user-" + token.getIssuerUserId(),
                "CERT_GENERATED",
                dataHash,
                Long.toString(token.getCredentialId()),
                tokenHash);
        String ledgerBlockHash = block != null ? block.getHash() : "";

        // Step A4: Build payload JSON that matches what ledger server expects
        // This MUST match the exact JSON structure the server will validate
        String payloadJson = String.format(
                "{\"issuerUserId\":%d,\"credentialId\":%d,\"tokenHash\":\"%s\",\"expiry\":%d,\"ledgerBlockHash\":\"%s\"}",
                token.getIssuerUserId(),
                token.getCredentialId(),
                tokenHash,
                token.getExpiry().getTime(),
                ledgerBlockHash);

        // Step A5: Sign the payload JSON using RSA private key
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(issuerPrivateKey);
        sig.update(payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] signature = sig.sign();
        String signatureBase64 = Base64.getEncoder().encodeToString(signature);

        // Step A6: Compute payloadHash for local verification (legacy field)
        String payloadData = tokenHash + "|" + token.getCredentialId() + "|" +
                issuerPublicKeyPem + "|" + token.getExpiry().getTime();
        String payloadHash = HashUtil.sha256(payloadData);

        // Build Certificate
        Certificate cert = new Certificate();
        cert.tokenHash = tokenHash; // Store tokenHash, NOT raw token
        cert.credentialId = token.getCredentialId();
        cert.issuerUserId = token.getIssuerUserId();
        cert.credentialHash = meta.credentialHash;
        cert.issuerPublicKeyPem = issuerPublicKeyPem;
        cert.expiryEpochMs = token.getExpiry().getTime();
        cert.createdAtMs = now;
        cert.payloadHash = payloadHash;
        cert.ledgerBlockHash = ledgerBlockHash;
        cert.signatureBase64 = signatureBase64;

        // Step A7: Register certificate with ledger server
        LedgerClient.storeCertificate(cert);

        CertificateParser.save(cert, outputPath);
        System.out.println("✓ Certificate created and registered with ledger server");
        System.out.println("  Token Hash: " + tokenHash);
        System.out.println("  Ledger Block: " + ledgerBlockHash);
        return cert;
    }

    /**
     * Create a signed certificate for a token.
     * 
     * @param token            Token object
     * @param issuerPrivateKey Issuer's RSA private key for signing
     * @param outputPath       Where to save certificate JSON
     * @return Created certificate
     */
    // Legacy createCertificate method removed in favor of metadata-bound version
    // above.

    /**
     * Validate token (check format, expiry, and revocation).
     * 
     * @param tokenString Token to validate
     * @return Token object if valid, null otherwise
     */
    public Token validateToken(String tokenString) {
        if (tokenString == null || tokenString.length() != 32 || !tokenString.matches("[0-9a-f]{32}")) {
            return null;
        }

        Token token = tokenRepository.findByTokenString(tokenString);

        if (token == null) {
            System.out.println("✗ Token not found");
            return null;
        }

        if (!token.isValid()) {
            if (token.isRevoked()) {
                System.out.println("✗ Token has been revoked");
            } else {
                System.out.println("✗ Token has expired");
            }
            return null;
        }

        return token;
    }

    /**
     * Revoke a token with persistence.
     * 
     * @param tokenString Token to revoke
     */
    public void revokeToken(String tokenString) {
        tokenRepository.revoke(tokenString);
        Token token = tokenRepository.findByTokenString(tokenString);

        // Revoke on ledger server using tokenHash
        String tokenHash = HashUtil.sha256(tokenString);
        boolean serverRevoked = LedgerClient.revokeToken(tokenHash);

        // Also append to local ledger (using token's issuer info if available)
        String dataHash = HashUtil.sha256("REVOKE:" + tokenHash);
        if (token != null) {
            ledgerService.appendBlock(token.getIssuerUserId(), "user_" + token.getIssuerUserId(), "TOKEN_REVOKED",
                    dataHash);
        } else {
            ledgerService.appendBlock(0L, "system", "TOKEN_REVOKED", dataHash);
        }

        if (serverRevoked) {
            System.out.println("✓ Token revoked locally and on ledger server");
        } else {
            System.out.println("✓ Token revoked locally (ledger server unreachable)");
        }
    }

    /**
     * List all tokens for a user.
     */
    public List<Token> listUserTokens(long userId) {
        return tokenRepository.findByIssuerUserId(userId);
    }

    /**
     * Clean up expired tokens.
     */
    public void cleanupExpiredTokens() {
        tokenRepository.deleteExpired();
    }
}
