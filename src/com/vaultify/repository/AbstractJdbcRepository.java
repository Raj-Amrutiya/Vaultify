package com.vaultify.repository;

import java.sql.*;
import com.vaultify.db.Database;

/**
 * Template-method style base for JDBC repositories.
 * Concrete classes supply SQL strings + mapping logic.
 */
public abstract class AbstractJdbcRepository<T> {

    protected abstract String tableName();

    protected abstract String insertSql();

    protected abstract void bindInsert(PreparedStatement stmt, T entity) throws SQLException;

    protected abstract T mapRow(ResultSet rs) throws SQLException;

    public T saveReturning(String idColumn, T entity) {
        try (Connection conn = Database.getConnection();
                PreparedStatement ps = conn.prepareStatement(insertSql(), Statement.RETURN_GENERATED_KEYS)) {
            bindInsert(ps, entity);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1); // assume first column is numeric PK
                    return findById(idColumn, id);
                }
            }
            return entity; // fallback no generated keys
        } catch (SQLException e) {
            throw new RepositoryException("Failed to insert into " + tableName(), e);
        }
    }

    public T findById(String idColumn, long id) {
        String sql = "SELECT * FROM " + tableName() + " WHERE " + idColumn + "=?";
        try (Connection conn = Database.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find by id in " + tableName(), e);
        }
    }

    protected ResultSet executeQuery(String sql, Object... params) throws SQLException {
        Connection conn = Database.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
        return ps.executeQuery(); // caller must close
    }
}
