package com.vaultify.repository;

import java.util.List;
import com.vaultify.models.CredentialMetadata;

/**
 * CredentialRepository abstraction for encrypted credential metadata.
 */
public interface CredentialRepository {
    /**
     * Persist metadata for a credential (returns generated numeric ID if available,
     * else -1).
     */
    long save(CredentialMetadata metadata, long userId);

    /** Lookup metadata by UUID string. */
    CredentialMetadata findByCredentialId(String credentialId);

    /** List all credentials for a specific user. */
    List<CredentialMetadata> findByUserId(long userId);

    /** Delete credential and its metadata. */
    void deleteByCredentialId(String credentialId);
}
