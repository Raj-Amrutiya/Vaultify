package com.vaultify.service;

import java.util.ArrayList;
import java.util.List;

import com.vaultify.client.LedgerClient;
import com.vaultify.models.LedgerBlock;

/**
 * Service facade for remote ledger operations.
 * All ledger operations go through the external ledger-server.
 */
public class LedgerService {

    public LedgerService() {
        // Ledger availability is checked on demand or by health check
    }

    public LedgerBlock appendBlock(String action, String dataHash) {
        return appendBlock(0L, "system", action, dataHash, null, null);
    }

    public LedgerBlock appendBlock(long userId, String username, String action, String dataHash) {
        return appendBlock(userId, username, action, dataHash, null, null);
    }

    public LedgerBlock appendBlock(long userId, String username, String action, String dataHash,
            String credentialId, String token) {
        return LedgerClient.appendBlock(userId, username, action, dataHash, credentialId, token);
    }

    public List<String> verifyIntegrity() {
        boolean valid = LedgerClient.verifyLedgerIntegrity();
        List<String> result = new ArrayList<>();
        if (valid) {
            result.add("✓ Ledger integrity verified");
        } else {
            result.add("✗ Ledger integrity check failed");
        }
        return result;
    }

    public List<LedgerBlock> getChain() {
        return LedgerClient.getAllBlocks();
    }

    public LedgerBlock getLatestBlock() {
        List<LedgerBlock> chain = getChain();
        return chain.isEmpty() ? null : chain.get(chain.size() - 1);
    }

    public LedgerBlock findBlockByHash(String hash) {
        return LedgerClient.getBlockByHash(hash);
    }
}
