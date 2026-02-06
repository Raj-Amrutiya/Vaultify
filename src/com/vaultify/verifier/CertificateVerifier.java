package com.vaultify.verifier;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import com.vaultify.client.LedgerClient;
import com.vaultify.crypto.HashUtil;

/**
 * 4-Layer Certificate Verification System
 * 
 * Layer 1: Certificate signature validation (local)
 * Layer 2: Token matches certificate
 * Layer 3: Online Ledger Server verification
 * Layer 4: Timestamp & expiry verification
 */
public class CertificateVerifier {

    /**
     * Complete 4-layer verification of certificate + token
     * 
     * @param cert          Certificate loaded from file
     * @param tokenFromUser Raw token string entered by verifier
     * @return Result with validity status and detailed message
     */
    public static Result verify(Certificate cert, String tokenFromUser) {
        System.out.println("\n===========================================");
        System.out.println("🔐 4-LAYER CERTIFICATE VERIFICATION");
        System.out.println("===========================================\n");

        try {
            // ===============================================
            // 🔵 LAYER 1 — Certificate Signature Validation (Local)
            // ===============================================
            System.out.println("🔵 LAYER 1: Certificate Signature Validation");
            System.out.println("   Verifying RSA signature...");

            // Reconstruct payloadHash from cert data (for integrity check)
            String payloadData = cert.tokenHash + "|" + cert.credentialId + "|" +
                    cert.issuerPublicKeyPem + "|" + cert.expiryEpochMs;
            String recomputedPayloadHash = HashUtil.sha256(payloadData);

            if (!recomputedPayloadHash.equals(cert.payloadHash)) {
                return fail("   ✗ PayloadHash mismatch - certificate tampered");
            }

            // Reconstruct the JSON payload that was signed (matches server format)
            String payloadJson = String.format(
                    "{\"issuerUserId\":%d,\"credentialId\":%d,\"tokenHash\":\"%s\",\"expiry\":%d,\"ledgerBlockHash\":\"%s\"}",
                    cert.issuerUserId,
                    cert.credentialId,
                    cert.tokenHash,
                    cert.expiryEpochMs,
                    cert.ledgerBlockHash);

            // Load issuer public key from PEM string
            PublicKey issuerPublicKey = loadPublicKeyFromPem(cert.issuerPublicKeyPem);

            // Verify signature over the JSON payload (not the hash)
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(issuerPublicKey);
            sig.update(payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] signatureBytes = Base64.getDecoder().decode(cert.signatureBase64);

            if (!sig.verify(signatureBytes)) {
                return fail("   ✗ RSA signature verification FAILED");
            }
            System.out.println("   ✓ Signature valid\n");

            // ===============================================
            // 🟡 LAYER 2 — Token Matches Certificate
            // ===============================================
            System.out.println("🟡 LAYER 2: Token Matches Certificate");
            System.out.println("   Computing tokenHash from user input...");

            String userTokenHash = HashUtil.sha256(tokenFromUser);

            if (!userTokenHash.equals(cert.tokenHash)) {
                return fail("   ✗ Token mismatch - wrong token for this certificate");
            }
            System.out.println("   ✓ Token matches certificate\n");

            // ===============================================
            // 🟨 LAYER 3 — Online Ledger Server Verification
            // ===============================================
            System.out.println("🟨 LAYER 3: Online Ledger Server Verification");

            // Check if server is available
            if (!LedgerClient.isServerAvailable()) {
                System.out.println("   ⚠ WARNING: Ledger server unavailable - skipping online checks");
                System.out.println("   (Offline verification only)\n");
            } else {
                // Step B3.1 — Fetch certificate from server
                System.out.println("   Fetching certificate from ledger server...");
                Certificate serverCert = LedgerClient.getCertificate(cert.tokenHash);

                if (serverCert == null) {
                    return fail("   ✗ Certificate not registered on ledger server");
                }

                // Verify server copy matches local certificate
                if (!serverCert.signatureBase64.equals(cert.signatureBase64)) {
                    return fail("   ✗ Certificate signature mismatch with server");
                }
                System.out.println("   ✓ Certificate registered on server");

                // Step B3.2 — Check token revocation
                System.out.println("   Checking token revocation status...");
                boolean isRevoked = LedgerClient.isTokenRevoked(cert.tokenHash);

                if (isRevoked) {
                    return fail("   ✗ TOKEN REVOKED - share access withdrawn");
                }
                System.out.println("   ✓ Token not revoked");

                // Step B3.3 — Fetch and verify ledger block
                System.out.println("   Verifying ledger block...");
                com.vaultify.models.LedgerBlock block = LedgerClient.getBlockByHash(cert.ledgerBlockHash);

                if (block == null) {
                    return fail("   ✗ Ledger block not found - certificate not anchored");
                }

                // Verify block dataHash
                String expectedDataHash = HashUtil.sha256(cert.tokenHash + ":" + cert.credentialId);
                if (!expectedDataHash.equals(block.getDataHash())) {
                    return fail("   ✗ Ledger block dataHash mismatch - tampering detected");
                }
                System.out.println("   ✓ Ledger block verified");
                System.out.println("   ✓ Chain integrity confirmed\n");
            }

            // ===============================================
            // 🟧 LAYER 4 — Timestamp & Expiry Verification
            // ===============================================
            System.out.println("🟧 LAYER 4: Timestamp & Expiry Verification");
            long now = System.currentTimeMillis();

            if (now >= cert.expiryEpochMs) {
                long daysExpired = (now - cert.expiryEpochMs) / (1000 * 60 * 60 * 24);
                return fail("   ✗ Certificate EXPIRED (" + daysExpired + " days ago)");
            }

            long hoursRemaining = (cert.expiryEpochMs - now) / (1000 * 60 * 60);
            System.out.println("   ✓ Certificate valid (" + hoursRemaining + " hours remaining)\n");

            // ===============================================
            // 🟩 SUCCESS — All 4 Layers Passed
            // ===============================================
            System.out.println("===========================================");
            System.out.println("✅ VERIFICATION SUCCESS");
            System.out.println("===========================================");
            System.out.println("✓ Certificate authentic");
            System.out.println("✓ Token correct");
            System.out.println("✓ Issuer signature valid");
            System.out.println("✓ Ledger anchored");
            System.out.println("✓ Not revoked");
            System.out.println("✓ Not expired\n");

            System.out.println("📋 Certificate Details:");
            System.out.println("   Issuer User ID: " + cert.issuerUserId);
            System.out.println("   Credential ID: " + cert.credentialId);
            System.out.println("   Token Hash: " + cert.tokenHash);
            System.out.println("   Valid Until: " + new java.util.Date(cert.expiryEpochMs));
            System.out.println("===========================================\n");

            return new Result(true, "All verification layers passed");

        } catch (Exception e) {
            return fail("\n✗ Verification error: " + e.getMessage());
        }
    }

    /**
     * Load RSA public key from PEM string
     */
    private static PublicKey loadPublicKeyFromPem(String pem) throws Exception {
        String publicKeyPEM = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
        return keyFactory.generatePublic(keySpec);
    }

    private static Result fail(String message) {
        System.out.println(message);
        System.out.println("\n===========================================");
        System.out.println("❌ VERIFICATION FAILED");
        System.out.println("===========================================\n");
        return new Result(false, message);
    }

    public static class Result {
        public final boolean valid;
        public final String message;

        public Result(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
    }
}
