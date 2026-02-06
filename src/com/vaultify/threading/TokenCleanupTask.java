package com.vaultify.threading;

import com.vaultify.service.TokenService;

/**
 * TokenCleanupTask - Periodic cleanup of expired tokens
 * 
 * Runs periodically to remove expired tokens from storage and keep the system
 * clean.
 */
public class TokenCleanupTask implements Runnable {
    private final TokenService tokenService;

    public TokenCleanupTask() {
        this.tokenService = new TokenService();
    }

    @Override
    public void run() {
        try {
            System.out.println("[TokenCleanup] Running expired token cleanup...");
            tokenService.cleanupExpiredTokens();
        } catch (Exception e) {
            System.err.println("[TokenCleanup] ✗ Error during cleanup: " + e.getMessage());
        }
    }
}
