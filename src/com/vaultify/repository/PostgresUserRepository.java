package com.vaultify.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.vaultify.db.Database;
import com.vaultify.models.User;

/**
 * JDBC-backed implementation of UserRepository using PostgreSQL.
 */
public class PostgresUserRepository extends AbstractJdbcRepository<User> implements UserRepository {

    @Override
    protected String tableName() {
        return "users";
    }

    @Override
    protected String insertSql() {
        return "INSERT INTO users (username, password_hash, public_key, private_key_encrypted, created_at) VALUES (?, ?, ?, ?, ?)";
    }

    @Override
    protected void bindInsert(PreparedStatement stmt, User user) throws SQLException {
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        }
        stmt.setString(1, user.getUsername());
        stmt.setString(2, user.getPasswordHash());
        stmt.setString(3, user.getPublicKey());
        stmt.setString(4, user.getPrivateKeyEncrypted());
        stmt.setTimestamp(5, user.getCreatedAt());
    }

    @Override
    protected User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setPublicKey(rs.getString("public_key"));
        u.setPrivateKeyEncrypted(rs.getString("private_key_encrypted"));
        u.setCreatedAt(rs.getTimestamp("created_at"));
        return u;
    }

    @Override
    public User save(User user) {
        User persisted = saveReturning("id", user);
        // Ensure caller's instance gets ID if different object returned
        user.setId(persisted.getId());
        return persisted;
    }

    @Override
    public User findById(long id) {
        return super.findById("id", id);
    }

    @Override
    public User findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = Database.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed findByUsername", e);
        }
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT * FROM users";
        List<User> list = new ArrayList<>();
        try (Connection conn = Database.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed findAll", e);
        }
        return list;
    }

    @Override
    public void delete(long id) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = Database.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed delete", e);
        }
    }
}
