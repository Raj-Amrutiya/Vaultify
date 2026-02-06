package com.vaultify.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import com.vaultify.util.Config;

public class Database {

    private static final String URL = Config.get("DB_URL" );
    private static final String USER = Config.get("DB_USER");
    private static final String PASSWORD = Config.get("DB_PASSWORD");

    private static volatile boolean SCHEMA_UPGRADED = false;

    public static Connection getConnection() {
        try {
            Class.forName("org.postgresql.Driver");
            Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
            if (!SCHEMA_UPGRADED) {
                upgradeSchema(conn);
                SCHEMA_UPGRADED = true;
            }
            return conn;
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException("Error connecting to the database", e);
        }
    }

    private static void upgradeSchema(Connection conn) {
        String[] statements = {
                "ALTER TABLE credentials ADD COLUMN IF NOT EXISTS encrypted_key TEXT",
                "ALTER TABLE credentials ADD COLUMN IF NOT EXISTS iv TEXT",
                "ALTER TABLE credentials ADD COLUMN IF NOT EXISTS data_hash TEXT",
                "ALTER TABLE credentials ADD COLUMN IF NOT EXISTS credential_hash TEXT",
                "ALTER TABLE credentials ADD COLUMN IF NOT EXISTS file_size BIGINT",
                "ALTER TABLE tokens ADD COLUMN IF NOT EXISTS issuer_user_id INT",
                "ALTER TABLE tokens ADD COLUMN IF NOT EXISTS revoked BOOLEAN DEFAULT FALSE",
                "ALTER TABLE tokens ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP",
                "CREATE INDEX IF NOT EXISTS idx_tokens_token ON tokens(token)",
                "CREATE INDEX IF NOT EXISTS idx_tokens_issuer ON tokens(issuer_user_id)",
                "CREATE INDEX IF NOT EXISTS idx_credentials_user ON credentials(user_id)"
        };
        try (Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                try {
                    stmt.execute(sql);
                } catch (SQLException e) {
                    System.err.println("[SchemaUpgrade] Failed: " + sql + " -> " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            System.err.println("[SchemaUpgrade] Unexpected failure upgrading schema: " + e.getMessage());
        }
    }
}