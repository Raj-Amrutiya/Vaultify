package com.vaultify.repository;

import com.vaultify.models.Token;
import com.vaultify.db.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PostgresTokenRepository implements TokenRepository {
    @Override
    public Token save(Token token) {
        String sql = "INSERT INTO tokens (credential_id, issuer_user_id, token, expiry, revoked) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, token.getCredentialId());
            ps.setLong(2, token.getIssuerUserId());
            ps.setString(3, token.getToken());
            ps.setTimestamp(4, token.getExpiry());
            ps.setBoolean(5, token.isRevoked());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    token.setId(keys.getLong(1));
                }
            }
            return token;
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save token", e);
        }
    }

    @Override
    public Token findByTokenString(String tokenString) {
        String sql = "SELECT * FROM tokens WHERE token = ?";
        try (Connection conn = Database.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenString);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Token t = new Token();
                    t.setId(rs.getLong("id"));
                    t.setCredentialId(rs.getLong("credential_id"));
                    t.setIssuerUserId(rs.getLong("issuer_user_id"));
                    t.setToken(rs.getString("token"));
                    t.setExpiry(rs.getTimestamp("expiry"));
                    t.setRevoked(rs.getBoolean("revoked"));
                    t.setCreatedAt(rs.getTimestamp("created_at"));
                    return t;
                }
            }
            return null;
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find token", e);
        }
    }

    @Override
    public List<Token> findByIssuerUserId(long userId) {
        String sql = "SELECT * FROM tokens WHERE issuer_user_id = ? ORDER BY created_at DESC";
        List<Token> list = new ArrayList<>();
        try (Connection conn = Database.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Token t = new Token();
                    t.setId(rs.getLong("id"));
                    t.setCredentialId(rs.getLong("credential_id"));
                    t.setIssuerUserId(rs.getLong("issuer_user_id"));
                    t.setToken(rs.getString("token"));
                    t.setExpiry(rs.getTimestamp("expiry"));
                    t.setRevoked(rs.getBoolean("revoked"));
                    t.setCreatedAt(rs.getTimestamp("created_at"));
                    list.add(t);
                }
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed listing tokens", e);
        }
        return list;
    }

    @Override
    public void revoke(String tokenString) {
        String sql = "UPDATE tokens SET revoked = TRUE WHERE token = ?";
        try (Connection conn = Database.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenString);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to revoke token", e);
        }
    }

    @Override
    public void deleteExpired() {
        String sql = "DELETE FROM tokens WHERE expiry < CURRENT_TIMESTAMP AND revoked = FALSE";
        try (Connection conn = Database.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed deleting expired tokens", e);
        }
    }
}
