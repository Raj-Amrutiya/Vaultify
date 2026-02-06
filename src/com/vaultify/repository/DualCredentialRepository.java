package com.vaultify.repository;

import java.util.List;
import java.util.Objects;

import com.vaultify.models.CredentialMetadata;

/**
 * Dual storage strategy for credential metadata: backup file + primary
 * database.
 */
public class DualCredentialRepository implements CredentialRepository {
    private final CredentialRepository primary;
    private final CredentialRepository backup;

    public DualCredentialRepository(CredentialRepository primary, CredentialRepository backup) {
        this.primary = Objects.requireNonNull(primary);
        this.backup = Objects.requireNonNull(backup);
    }

    @Override
    public long save(CredentialMetadata metadata, long userId) {
        backup.save(metadata, userId); // ensure file copy exists
        try {
            return primary.save(metadata, userId);
        } catch (RepositoryException e) {
            System.err.println("[DualCredentialRepository] Primary save failed: " + e.getMessage());
            return metadata.id > 0 ? metadata.id : -1L;
        }
    }

    @Override
    public CredentialMetadata findByCredentialId(String credentialId) {
        try {
            CredentialMetadata m = primary.findByCredentialId(credentialId);
            if (m != null)
                return m;
        } catch (RepositoryException e) {
            System.err.println("[DualCredentialRepository] Primary find error: " + e.getMessage());
        }
        return backup.findByCredentialId(credentialId);
    }

    @Override
    public List<CredentialMetadata> findByUserId(long userId) {
        try {
            List<CredentialMetadata> list = primary.findByUserId(userId);
            if (!list.isEmpty())
                return list;
        } catch (RepositoryException e) {
            System.err.println("[DualCredentialRepository] Primary list error: " + e.getMessage());
        }
        return backup.findByUserId(userId);
    }

    @Override
    public void deleteByCredentialId(String credentialId) {
        try {
            primary.deleteByCredentialId(credentialId);
        } catch (RepositoryException e) {
            System.err.println("[DualCredentialRepository] Primary delete failed: " + e.getMessage());
        }
        backup.deleteByCredentialId(credentialId);
    }
}
