package com.vaultify.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.vaultify.db.Database;
import com.vaultify.models.CredentialMetadata;
import com.vaultify.models.CredentialType;

/**
 * JDBC-backed implementation of CredentialRepository.
 * Maps CredentialMetadata onto credentials table columns.
 */
public class PostgresCredentialRepository implements CredentialRepository {

    @Override
    public long save(CredentialMetadata meta, long userId) {
        String sql = "INSERT INTO credentials (user_id, filename, filepath, encrypted_key, iv, data_hash, credential_hash, file_size, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = Database.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setString(2, meta.filename);
            ps.setString(3, "vault_data/credentials/" + meta.credentialIdString + ".bin");
            ps.setString(4, meta.encryptedKeyBase64);
            ps.setString(5, meta.ivBase64);
            ps.setString(6, meta.dataHash);
            ps.setString(7, meta.credentialHash);
            ps.setLong(8, meta.fileSize);
            ps.setTimestamp(9, new Timestamp(meta.timestamp));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    meta.id = (int) id;
                    return id;
                }
            }
            return -1L;
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save credential metadata", e);
        }
    }

    @Override
    public CredentialMetadata findByCredentialId(String credentialId) {
        String sql = "SELECT * FROM credentials WHERE filepath = ?";
        try (Connection conn = Database.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "vault_data/credentials/" + credentialId + ".bin");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    CredentialMetadata meta = hydrate(rs);
                    meta.credentialIdString = credentialId; // override derived id
                    return meta;
                }
            }
            return null;
        } catch (SQLException e) {
            throw new RepositoryException("Failed findByCredentialId", e);
        }
    }

    @Override
    public List<CredentialMetadata> findByUserId(long userId) {
        List<CredentialMetadata> list = new ArrayList<>();
        String sql = "SELECT * FROM credentials WHERE user_id = ?";
        try (Connection conn = Database.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CredentialMetadata meta = hydrate(rs);
                    list.add(meta);
                }
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed findByUserId", e);
        }
        return list;
    }

    @Override
    public void deleteByCredentialId(String credentialId) {
        String sql = "DELETE FROM credentials WHERE filepath = ?";
        try (Connection conn = Database.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "vault_data/credentials/" + credentialId + ".bin");
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed deleteByCredentialId", e);
        }
    }

    private CredentialMetadata hydrate(ResultSet rs) throws SQLException {
        CredentialMetadata meta = new CredentialMetadata();
        meta.id = rs.getInt("id");
        meta.userId = rs.getLong("user_id");
        meta.filename = rs.getString("filename");
        meta.type = CredentialType.FILE;
        meta.timestamp = rs.getTimestamp("created_at").getTime();
        String path = rs.getString("filepath");
        String filename = new java.io.File(path).getName();
        meta.credentialIdString = filename.replace(".bin", "");

        // Hydrate from flattened columns
        meta.encryptedKeyBase64 = rs.getString("encrypted_key");
        meta.ivBase64 = rs.getString("iv");
        meta.dataHash = rs.getString("data_hash");
        meta.credentialHash = rs.getString("credential_hash");
        meta.fileSize = rs.getLong("file_size");

        return meta;
    }

}
