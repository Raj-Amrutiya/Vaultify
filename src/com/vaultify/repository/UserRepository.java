package com.vaultify.repository;

import java.util.List;
import com.vaultify.models.User;

/**
 * UserRepository abstraction encapsulating all persistence operations for User.
 * Implementations: PostgresUserRepository, FileUserRepository (fallback).
 */
public interface UserRepository {
    User save(User user);

    User findById(long id);

    User findByUsername(String username);

    List<User> findAll();

    void delete(long id);
}
