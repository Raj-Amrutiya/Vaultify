package com.vaultify.repository;

import java.util.List;
import java.util.Objects;

import com.vaultify.models.User;

/**
 * Polymorphic repository combining a primary (e.g., Postgres) and a backup
 * (file) store.
 * Saves to backup first, then attempts primary. Reads prefer primary, fallback
 * to backup.
 */
public class DualUserRepository implements UserRepository {
    private final UserRepository primary;
    private final UserRepository backup;

    public DualUserRepository(UserRepository primary, UserRepository backup) {
        this.primary = Objects.requireNonNull(primary);
        this.backup = Objects.requireNonNull(backup);
    }

    @Override
    public User save(User user) {
        // Write to backup first to ensure at least one durable copy
        backup.save(user);
        try {
            return primary.save(user);
        } catch (RepositoryException e) {
            System.err.println("[DualUserRepository] Primary save failed: " + e.getMessage());
            return user; // backup already persisted
        }
    }

    @Override
    public User findById(long id) {
        try {
            User u = primary.findById(id);
            if (u != null)
                return u;
        } catch (RepositoryException e) {
            System.err.println("[DualUserRepository] Primary findById error: " + e.getMessage());
        }
        return backup.findById(id); // likely null
    }

    @Override
    public User findByUsername(String username) {
        try {
            User u = primary.findByUsername(username);
            if (u != null)
                return u;
        } catch (RepositoryException e) {
            System.err.println("[DualUserRepository] Primary findByUsername error: " + e.getMessage());
        }
        return backup.findByUsername(username);
    }

    @Override
    public List<User> findAll() {
        try {
            return primary.findAll();
        } catch (RepositoryException e) {
            System.err.println("[DualUserRepository] Primary findAll error: " + e.getMessage());
            return backup.findAll();
        }
    }

    @Override
    public void delete(long id) {
        try {
            primary.delete(id);
        } catch (RepositoryException e) {
            System.err.println("[DualUserRepository] Primary delete failed: " + e.getMessage());
        }
        // backup lacks id delete; ignoring
    }
}
