package com.vaultify.repository;

import java.util.List;
import java.util.Objects;

import com.vaultify.models.Token;

public class DualTokenRepository implements TokenRepository {
    private final TokenRepository primary;
    private final TokenRepository backup;

    public DualTokenRepository(TokenRepository primary, TokenRepository backup) {
        this.primary = Objects.requireNonNull(primary);
        this.backup = Objects.requireNonNull(backup);
    }

    @Override
    public Token save(Token token) {
        backup.save(token);
        try {
            return primary.save(token);
        } catch (RepositoryException e) {
            System.err.println("[DualTokenRepository] Primary save failed: " + e.getMessage());
            return token;
        }
    }

    @Override
    public Token findByTokenString(String tokenString) {
        try {
            Token t = primary.findByTokenString(tokenString);
            if (t != null)
                return t;
        } catch (RepositoryException e) {
            System.err.println("[DualTokenRepository] Primary find error: " + e.getMessage());
        }
        return backup.findByTokenString(tokenString);
    }

    @Override
    public List<Token> findByIssuerUserId(long userId) {
        try {
            List<Token> list = primary.findByIssuerUserId(userId);
            if (!list.isEmpty())
                return list;
        } catch (RepositoryException e) {
            System.err.println("[DualTokenRepository] Primary list error: " + e.getMessage());
        }
        return backup.findByIssuerUserId(userId);
    }

    @Override
    public void revoke(String tokenString) {
        try {
            primary.revoke(tokenString);
        } catch (RepositoryException e) {
            System.err.println("[DualTokenRepository] Primary revoke failed: " + e.getMessage());
        }
        backup.revoke(tokenString);
    }

    @Override
    public void deleteExpired() {
        try {
            primary.deleteExpired();
        } catch (RepositoryException e) {
            System.err.println("[DualTokenRepository] Primary deleteExpired failed: " + e.getMessage());
        }
        backup.deleteExpired();
    }
}
