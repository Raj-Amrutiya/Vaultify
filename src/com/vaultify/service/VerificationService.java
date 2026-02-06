package com.vaultify.service;

import java.nio.file.Path;

import com.vaultify.crypto.HashUtil;
import com.vaultify.verifier.Certificate;
import com.vaultify.verifier.CertificateParser;
import com.vaultify.verifier.CertificateVerifier;

/**
 * VerificationService - generates signed certificates for share tokens and
 * verifies them.
 *
 * NOTE: This implementation expects the issuer's RSA private key to be stored
 * as PKCS#8 PEM
 * at a path the caller provides (or can be derived from user record).
 */
public class VerificationService {
    private final LedgerService ledgerService;

    public VerificationService() {
        this.ledgerService = new LedgerService();
    }

    public VerificationService(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    // Certificate generation moved to TokenService. This service now focuses solely
    // on verification.

    /**
     * Verify certificate using 4-layer verification.
     * Requires the raw token string from the user.
     * Returns CertificateVerifier.Result describing validity and message.
     * Appends VALIDATE_CERT entry to ledger.
     */
    public CertificateVerifier.Result verifyCertificate(Path certPath, String token) throws Exception {
        if (certPath == null || !certPath.toFile().exists()) {
            throw new ServiceException("Certificate file not found: " + certPath);
        }
        if (token == null || token.isEmpty()) {
            throw new ServiceException("Token cannot be empty");
        }
        Certificate cert = CertificateParser.parse(certPath);

        // Perform complete 4-layer verification
        CertificateVerifier.Result res = CertificateVerifier.verify(cert, token);

        // Append verification result to ledger
        String dataHash = HashUtil.sha256(cert.tokenHash + "|valid=" + res.valid);
        ledgerService.appendBlock(0L, "verifier", "VALIDATE_CERT", dataHash);
        return res;
    }
}
