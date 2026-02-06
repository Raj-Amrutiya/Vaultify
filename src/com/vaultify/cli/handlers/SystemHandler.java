package com.vaultify.cli.handlers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import com.vaultify.service.AuthService;
import com.vaultify.service.LedgerService;
import com.vaultify.util.Config;
import com.vaultify.util.PathValidator;

public class SystemHandler {
    private final LedgerService ledgerService;
    private final AuthService authService;
    private static final Path STORAGE_DIR = Paths.get("vault_data/credentials");
    private static final Path KEYS_DIR = Paths.get("vault_data/keys");

    public SystemHandler(LedgerService ledgerService, AuthService authService) {
        this.ledgerService = ledgerService;
        this.authService = authService;
    }

    public void showStats() {
        System.out.println("\n=== Vaultify Stats ===");
        try (java.sql.Connection conn = com.vaultify.db.Database.getConnection()) {

            long users = queryCount(conn, "SELECT COUNT(*) FROM users");
            long credentials = queryCount(conn, "SELECT COUNT(*) FROM credentials");
            long tokens = queryCount(conn, "SELECT COUNT(*) FROM tokens");

            System.out.println("Users       : " + users);
            System.out.println("Credentials : " + credentials);
            System.out.println("Tokens      : " + tokens);

        } catch (Exception e) {
            System.out.println("✗ Could not query database: " + e.getMessage());
        }

        // Disk usage for vault_data
        try {
            long[] usage = directorySizeAndCount(STORAGE_DIR);
            if (usage != null) {
                System.out.println("\nStorage directory: " + STORAGE_DIR.toAbsolutePath());
                System.out.println("  Files: " + usage[1]);
                System.out.println("  Size : " + PathValidator.formatSize(usage[0]));
            } else {
                System.out.println("\nStorage directory not found: " + STORAGE_DIR.toAbsolutePath());
            }
        } catch (Exception ex) {
            System.out.println("✗ Could not inspect storage dir: " + ex.getMessage());
        }

        // Ledger status
        try {
            boolean ledgerAvailable = com.vaultify.client.LedgerClient.isServerAvailable();
            System.out.println("\nLedger server : " + (ledgerAvailable ? "✓ Available" : "✗ Not available"));
            if (ledgerAvailable) {
                List<com.vaultify.models.LedgerBlock> blocks = com.vaultify.client.LedgerClient.getAllBlocks();
                System.out.println("  Blocks       : " + blocks.size());
            }
        } catch (Exception e) {
            System.out.println("✗ Could not contact ledger: " + e.getMessage());
        }

        System.out.println("======================\n");
    }

    public void showHealth() {
        System.out.println("\n=== Vaultify Health Check ===");

        // DB
        System.out.print("Database: ");
        try (java.sql.Connection conn = com.vaultify.db.Database.getConnection()) {
            if (conn != null && !conn.isClosed()) {
                System.out.println("✓ OK (" + conn.getMetaData().getURL() + ")");
            } else {
                System.out.println("✗ Failed to open connection");
            }
        } catch (SQLException e) {
            System.out.println("✗ ERROR - " + e.getMessage());
        }

        // Ledger
        System.out.print("Ledger Server: ");
        try {
            boolean ledgerOk = com.vaultify.client.LedgerClient.isServerAvailable();
            System.out.println(ledgerOk ? "✓ OK" : "✗ Not reachable");
        } catch (Exception e) {
            System.out.println("✗ ERROR - " + e.getMessage());
        }

        // Storage dirs and permissions
        System.out.print("Storage dir (read/write): ");
        Path storage = STORAGE_DIR;
        try {
            if (!Files.exists(storage)) {
                System.out.println("✗ Missing (" + storage.toAbsolutePath() + ")");
            } else {
                boolean r = Files.isReadable(storage);
                boolean w = Files.isWritable(storage);
                System.out.println((r && w) ? "✓ OK" : "✗ Permissions issue (r:" + r + " w:" + w + ")");
            }
        } catch (Exception e) {
            System.out.println("✗ ERROR - " + e.getMessage());
        }

        System.out.print("Keys dir: ");
        try {
            if (!Files.exists(KEYS_DIR)) {
                System.out.println("✗ Missing (" + KEYS_DIR.toAbsolutePath() + ")");
            } else {
                System.out.println("✓ OK (" + KEYS_DIR.toAbsolutePath() + ")");
            }
        } catch (Exception e) {
            System.out.println("✗ ERROR - " + e.getMessage());
        }

        // Quick verification: can we read an example public key if any user exists?
        try {
            long userCount = queryCount(com.vaultify.db.Database.getConnection(), "SELECT COUNT(*) FROM users");
            System.out.print("Public keys present for users: ");
            if (userCount == 0) {
                System.out.println("⚠ No users found");
            } else {
                boolean anyKey = false;
                try (java.sql.Connection c = com.vaultify.db.Database.getConnection();
                        PreparedStatement ps = c
                                .prepareStatement("SELECT public_key FROM users WHERE public_key IS NOT NULL LIMIT 1");
                        ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String pk = rs.getString(1);
                        if (pk != null && !pk.trim().isEmpty()) {
                            anyKey = true;
                        }
                    }
                }
                System.out.println(anyKey ? "✓ Found" : "✗ Missing");
            }
        } catch (SQLException e) {
            System.out.println("✗ ERROR - " + e.getMessage());
        } catch (Exception e) {
            // ignore
        }

        System.out.println("================================\n");
    }

    public void reconcileAndReport(Scanner scanner) {
        System.out.println("\n=== Reconciliation & Drift Report ===");
        System.out.println("This operation will inspect:");
        System.out.println("  - DB 'credentials' table entries");
        System.out.println("  - Stored files under: " + STORAGE_DIR.toAbsolutePath());
        System.out.println("  - Ledger blocks (if ledger server available)\n");

        System.out.print("Proceed? [y/N]: ");
        String proceed = scanner.nextLine().trim().toLowerCase();
        if (!proceed.equals("y") && !proceed.equals("yes")) {
            System.out.println("Cancelled.");
            return;
        }

        try (java.sql.Connection conn = com.vaultify.db.Database.getConnection()) {

            // 1) Load DB credentials
            Map<String, DbCred> dbCreds = new HashMap<>();
            try {
                // Detect whether 'credential_id_string' exists; fall back safely if not present
                boolean hasCredIdString = columnExists(conn, "credentials", "credential_id_string");

                StringBuilder select = new StringBuilder("SELECT id, filename, file_size, user_id");
                if (hasCredIdString)
                    select.append(", credential_id_string");
                select.append(" FROM credentials");

                try (PreparedStatement ps = conn.prepareStatement(select.toString());
                        ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        String cid = null;
                        if (hasCredIdString) {
                            try {
                                cid = rs.getString("credential_id_string");
                            } catch (SQLException ignore) {
                                cid = null;
                            }
                        }

                        String filename = null;
                        try {
                            filename = rs.getString("filename");
                        } catch (SQLException ignore) {
                            filename = null;
                        }

                        long size = 0L;
                        try {
                            size = rs.getLong("file_size");
                        } catch (SQLException ignore) {
                            size = 0L;
                        }

                        long userId = 0L;
                        try {
                            userId = rs.getLong("user_id");
                        } catch (SQLException ignore) {
                            userId = 0L;
                        }

                        // If credential id string missing, fall back to filename or id-based
                        // placeholder
                        String effectiveCid = cid;
                        boolean isFallback = false;
                        if (effectiveCid == null || effectiveCid.trim().isEmpty()) {
                            isFallback = true;
                            if (filename != null && !filename.trim().isEmpty()) {
                                effectiveCid = filename;
                            } else {
                                effectiveCid = String.valueOf(id);
                            }
                        }

                        dbCreds.put(effectiveCid, new DbCred(effectiveCid, id, filename, size, userId, isFallback));
                    }
                }

            } catch (SQLException ex) {
                System.out.println("✗ Failed to read credentials from DB: " + ex.getMessage());
            }

            System.out.println("DB credentials found: " + dbCreds.size());

            // 2) Inspect storage directory for files (map: credential-id -> path)
            Map<String, Path> storedFiles = new HashMap<>();
            if (Files.exists(STORAGE_DIR) && Files.isDirectory(STORAGE_DIR)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(STORAGE_DIR)) {
                    for (Path p : ds) {
                        if (Files.isRegularFile(p)) {
                            String name = p.getFileName().toString();
                            String key = name;
                            if (name.contains(".")) {
                                key = name.substring(0, name.indexOf('.'));
                            }
                            storedFiles.put(key, p);
                            storedFiles.put(name, p);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("✗ Could not list storage dir: " + e.getMessage());
                }
            } else {
                System.out.println("✗ Storage directory not present: " + STORAGE_DIR.toAbsolutePath());
            }

            System.out.println("Stored files discovered: " + storedFiles.size());

            // 3) Ledger extraction (best-effort, reflection-friendly)
            Set<String> ledgerCredIds = new HashSet<>();
            boolean ledgerAvailable = false;
            try {
                ledgerAvailable = com.vaultify.client.LedgerClient.isServerAvailable();
                if (ledgerAvailable) {
                    List<com.vaultify.models.LedgerBlock> blocks = com.vaultify.client.LedgerClient.getAllBlocks();

                    for (com.vaultify.models.LedgerBlock b : blocks) {
                        String maybe = extractTextFromLedgerBlock(b);
                        if (maybe != null && !maybe.isEmpty()) {
                            String[] tokens = maybe.split("[\\s,\\:\\{\\}\\[\\]\"'\\(\\)<>]+");
                            for (String tok : tokens) {
                                if (tok.length() >= 6 && tok.length() <= 128) {
                                    if (tok.matches(".*[0-9].*") || tok.contains("-")) {
                                        ledgerCredIds.add(tok);
                                    }
                                }
                            }
                        }
                    }
                    System.out.println("Ledger referenced credential-like tokens found: " + ledgerCredIds.size());
                } else {
                    System.out.println("Ledger server not available; skipping ledger checks.");
                }
            } catch (Exception e) {
                System.out.println("✗ Ledger check error: " + e.getMessage());
            }

            // 4) Compare sets and produce report
            List<String> missingFiles = new ArrayList<>();
            List<String> orphanFiles = new ArrayList<>();
            List<String> mismatchedSizes = new ArrayList<>();

            // DB -> File
            for (DbCred d : dbCreds.values()) {
                boolean matched = false;
                if (storedFiles.containsKey(d.credentialId)) {
                    matched = true;
                    Path p = storedFiles.get(d.credentialId);
                    try {
                        long actual = Files.size(p);
                        if (actual != d.fileSize) {
                            mismatchedSizes.add(d.credentialId + " (DB: " + d.fileSize + " vs FS: " + actual + ") -> "
                                    + p.getFileName());
                        }
                    } catch (IOException ioe) {
                        mismatchedSizes.add(d.credentialId + " (could not read file) -> " + p.getFileName());
                    }
                } else {
                    if (d.filename != null && storedFiles.containsKey(d.filename)) {
                        matched = true;
                        Path p = storedFiles.get(d.filename);
                        try {
                            long actual = Files.size(p);
                            if (actual != d.fileSize) {
                                mismatchedSizes.add(d.credentialId + " (DB: " + d.fileSize + " vs FS: " + actual
                                        + ") -> " + p.getFileName());
                            }
                        } catch (IOException ioe) {
                            mismatchedSizes.add(d.credentialId + " (could not read file) -> " + p.getFileName());
                        }
                    }
                }

                if (!matched) {
                    if (d.isFallbackId) {
                        missingFiles.add(d.credentialId + " (ID missing in DB, expected file: " + d.filename + ")");
                    } else {
                        missingFiles.add(d.credentialId + " (expected file: " + d.filename + ")");
                    }
                }
            }

            // File -> DB (orphan files)
            // Start with all files as potential orphans
            Set<Path> potentialOrphans = new HashSet<>(storedFiles.values());

            // Remove files that are referenced by DB entries
            for (DbCred d : dbCreds.values()) {
                if (storedFiles.containsKey(d.credentialId)) {
                    potentialOrphans.remove(storedFiles.get(d.credentialId));
                } else if (d.filename != null && storedFiles.containsKey(d.filename)) {
                    potentialOrphans.remove(storedFiles.get(d.filename));
                }
            }

            for (Path p : potentialOrphans) {
                orphanFiles.add(p.getFileName().toString() + " -> " + p.toAbsolutePath());
            }

            // Ledger -> DB: tokens present in ledger but not in DB
            List<String> ledgerOnly = new ArrayList<>();
            if (!ledgerCredIds.isEmpty()) {
                for (String ledgerId : ledgerCredIds) {
                    if (!dbCreds.containsKey(ledgerId)) {
                        ledgerOnly.add(ledgerId);
                    }
                }
            }

            // 5) Present report
            System.out.println("\n=== Reconciliation Results ===");
            System.out.println("DB credentials: " + dbCreds.size());
            System.out.println("Stored files : " + storedFiles.size());
            if (ledgerAvailable) {
                System.out.println("Ledger tokens: " + ledgerCredIds.size());
            }

            System.out.println("\n-- Missing files for DB entries (" + missingFiles.size() + ")");
            missingFiles.stream().limit(200).forEach(s -> System.out.println("  • " + s));
            if (missingFiles.size() > 200)
                System.out.println("  ... (" + (missingFiles.size() - 200) + " more)");

            System.out.println("\n-- Orphan files on disk (" + orphanFiles.size() + ")");
            orphanFiles.stream().limit(200).forEach(s -> System.out.println("  • " + s));
            if (orphanFiles.size() > 200)
                System.out.println("  ... (" + (orphanFiles.size() - 200) + " more)");

            System.out.println("\n-- Mismatched sizes (" + mismatchedSizes.size() + ")");
            mismatchedSizes.stream().limit(200).forEach(s -> System.out.println("  • " + s));
            if (mismatchedSizes.size() > 200)
                System.out.println("  ... (" + (mismatchedSizes.size() - 200) + " more)");

            if (ledgerAvailable) {
                System.out.println("\n-- Ledger-only tokens/ids (" + ledgerOnly.size() + ")");
                ledgerOnly.stream().limit(200).forEach(s -> System.out.println("  • " + s));
                if (ledgerOnly.size() > 200)
                    System.out.println("  ... (" + (ledgerOnly.size() - 200) + " more)");
            }

            System.out.println("\n=== Suggested next steps ===");
            System.out.println(
                    "  1) Investigate missing files: check backups or device uploads for those credential IDs.");
            System.out.println(
                    "  2) Investigate orphan files: determine if they belong to old users or are transient temp files.");
            System.out.println(
                    "  3) For mismatched sizes: re-download from storage or re-encrypt original source if available.");
            if (ledgerAvailable) {
                System.out.println(
                        "  4) For ledger-only IDs: inspect ledger block payloads (structure differs between deployments).");
            }
            System.out.println("\nReconciliation finished.\n");

        } catch (SQLException e) {
            System.out.println("✗ Reconciliation failed due to DB error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("✗ Reconciliation failed: " + e.getMessage());
        }
    }

    public void verifyLedger() {
        try {
            List<String> errors = ledgerService.verifyIntegrity();

            if (errors.isEmpty()) {
                System.out.println("✓ Ledger integrity verified - no issues found");
                System.out.println("  Total blocks: " + ledgerService.getChain().size());
            } else {
                System.out.println("✗ Ledger integrity check FAILED:");
                errors.forEach(err -> System.out.println("  - " + err));
            }
        } catch (Exception e) {
            System.out.println("✗ Verification error: " + e.getMessage());
        }
    }

    public void testLedgerConnection() {
        System.out.println("================================");
        System.out.println("Testing Ledger Server Connection");
        System.out.println("================================");

        System.out.print("Checking server availability... ");
        boolean available = com.vaultify.client.LedgerClient.isServerAvailable();

        if (available) {
            System.out.println("✓ Connected!");
            System.out.println("\nFetching ledger statistics...");

            java.util.List<com.vaultify.models.LedgerBlock> blocks = com.vaultify.client.LedgerClient.getAllBlocks();
            System.out.println("✓ Total blocks: " + blocks.size());

            if (!blocks.isEmpty()) {
                com.vaultify.models.LedgerBlock latest = blocks.get(blocks.size() - 1);
                // defensive: try getIndex/getAction if available
                try {
                    System.out.println("✓ Latest block index: " + latest.getIndex());
                } catch (Throwable ignored) {
                }
                try {
                    System.out.println("✓ Latest block action: " + latest.getAction());
                } catch (Throwable ignored) {
                }
            }

            System.out.print("\nVerifying chain integrity... ");
            boolean valid = com.vaultify.client.LedgerClient.verifyLedgerIntegrity();
            if (valid) {
                System.out.println("✓ Valid");
            } else {
                System.out.println("✗ Invalid");
            }
        } else {
            System.out.println("✗ Not available");
            System.out.println("\n⚠ Make sure the ledger server is running:");
            System.out.println("  cd ledger-server");
            System.out.println("  npm start");
        }
    }

    public void testDatabaseConnection() {
        System.out.println("\n=== Database Connection Test ===");

        try (java.sql.Connection conn = com.vaultify.db.Database.getConnection()) {
            System.out.println("✓ Connection successful!");
            System.out.println("  URL: " + conn.getMetaData().getURL());
            System.out.println("  Database: " + conn.getCatalog());
            System.out.println("  User: " + conn.getMetaData().getUserName());

            // List all tables in the database
            System.out.println("\n=== Available Tables ===");
            try (java.sql.ResultSet rs = conn.getMetaData().getTables(null, "public", "%", new String[] { "TABLE" })) {
                boolean foundTables = false;
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    System.out.println("  • " + tableName);
                    foundTables = true;
                }
                if (!foundTables) {
                    System.out.println("  ⚠ No tables found! init.sql did not execute.");
                    System.out.println("\n  Fix: docker compose down -v && docker compose up");
                }
            }

            // Describe each expected table and validate schema
            System.out.println("\n=== Table Schemas ===");
            String[] expectedTables = { "users", "credentials", "tokens" };
            boolean schemaValid = true;

            for (String table : expectedTables) {
                try (java.sql.ResultSet rs = conn.getMetaData().getColumns(null, "public", table, null)) {
                    if (!rs.isBeforeFirst()) {
                        System.out.println("\n✗ Table '" + table + "' does NOT exist");
                        schemaValid = false;
                        continue;
                    }

                    System.out.println("\n✓ Table: " + table);
                    int colCount = 0;
                    while (rs.next()) {
                        String colName = rs.getString("COLUMN_NAME");
                        String colType = rs.getString("TYPE_NAME");
                        int colSize = rs.getInt("COLUMN_SIZE");
                        String nullable = rs.getString("IS_NULLABLE").equals("YES") ? "NULL" : "NOT NULL";
                        System.out.println("    - " + colName + " (" + colType +
                                (colSize > 0 ? "(" + colSize + ")" : "") + ") " + nullable);
                        colCount++;
                    }

                    // Validate users table has crypto fields
                    if (table.equals("users") && colCount < 6) {
                        System.out.println(
                                "    ⚠ WARNING: users table has old schema (missing public_key, private_key_encrypted)");
                        schemaValid = false;
                    }
                }
            }

            if (!schemaValid) {
                System.out.println("\n⚠ SCHEMA MISMATCH DETECTED!");
                System.out.println("\nThe database was initialized with an old schema.");
                System.out.println("You MUST recreate the database volume:\n");
                System.out.println("  1. Exit this container (Ctrl+C)");
                System.out.println("  2. Run: docker compose down -v");
                System.out.println("  3. Run: docker compose up\n");
                System.out.println("The -v flag is CRITICAL - it removes the old database volume.");
            } else {
                System.out.println("\n✓ All schemas valid!");
            }

        } catch (java.sql.SQLException e) {
            System.out.println("✗ Connection failed!");
            System.out.println("  Error: " + e.getMessage());
        }
        System.out.println("================================\n");
    }

    public void showDevModeStatus() {
        System.out.println("\n╔════════════════════════════════════════════════════════╗");
        System.out.println("║           DEVELOPMENT MODE STATUS                      ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");

        boolean devMode = Config.isDevMode();
        System.out.println("\nCurrent Mode: " + (devMode ? "🔧 DEVELOPMENT" : "🔒 PRODUCTION"));
        System.out.println("Config Source: config.properties");
        System.out.println("Setting: dev.mode=" + devMode);

        if (devMode) {
            System.out.println("\n⚠️  DEVELOPMENT MODE ENABLED");
            System.out.println("\nAvailable dev commands:");
            System.out.println("  • test-db          - Test database connectivity");
            System.out.println("  • test-ledger      - Test remote ledger server");
            System.out.println("  • reset-all        - ⚠️  DESTRUCTIVE: Delete all data");
            System.out.println("  • dev-mode         - Show this status");
            System.out.println("\n⚠️  WARNING: reset-all will PERMANENTLY delete:");
            System.out.println("  - All database tables (users, credentials, tokens)");
            System.out.println("  - All vault storage files");
            System.out.println("  - All keys and certificates");
            System.out.println("  - Ledger data (ledger.json)");
        } else {
            System.out.println("\n✓ Production mode - dev commands are disabled");
            System.out.println("\nTo enable dev mode, set 'dev.mode=true' in config.properties");
        }
        System.out.println();
    }

    public void resetAll(Scanner scanner) {
        System.out.println("\n╔════════════════════════════════════════════════════════╗");
        System.out.println("║                ⚠️  DANGER ZONE ⚠️                       ║");
        System.out.println("║           COMPLETE SYSTEM RESET                        ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");

        System.out.println("\n⚠️  This will PERMANENTLY delete:");
        System.out.println("  ✗ All user accounts");
        System.out.println("  ✗ All credentials and encrypted files");
        System.out.println("  ✗ All access tokens");
        System.out.println("  ✗ All keys (public/private)");
        System.out.println("  ✗ All certificates");
        System.out.println("  ✗ All ledger audit trail");
        System.out.println("  ✗ All file storage data");

        System.out.print("\nType 'DELETE' (uppercase) to confirm: ");
        String confirmation = scanner.nextLine().trim();

        if (!"DELETE".equals(confirmation)) {
            System.out.println("✗ Reset cancelled.");
            return;
        }

        System.out.print("\nAre you absolutely sure? Type 'YES' to proceed: ");
        String secondConfirm = scanner.nextLine().trim();

        if (!"YES".equals(secondConfirm)) {
            System.out.println("✗ Reset cancelled.");
            return;
        }

        System.out.println("\n🔄 Starting system reset...\n");

        int errors = 0;

        // 1. Reset Database
        System.out.println("[1/4] Resetting database tables...");
        try {
            java.sql.Connection conn = com.vaultify.db.Database.getConnection();

            // Disable foreign key checks temporarily
            try (PreparedStatement stmt = conn.prepareStatement("SET CONSTRAINTS ALL DEFERRED")) {
                stmt.execute();
            } catch (SQLException e) {
                // Try alternative for PostgreSQL
                try (PreparedStatement stmt = conn.prepareStatement("SET session_replication_role = 'replica'")) {
                    stmt.execute();
                }
            }

            // Delete in order to respect foreign keys
            String[] tables = { "tokens", "credentials", "users" };
            for (String table : tables) {
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM " + table)) {
                    int deleted = stmt.executeUpdate();
                    System.out.println("  ✓ Deleted " + deleted + " rows from " + table);
                } catch (SQLException e) {
                    System.out.println("  ✗ Failed to clear " + table + ": " + e.getMessage());
                    errors++;
                }
            }

            // Re-enable foreign key checks
            try (PreparedStatement stmt = conn.prepareStatement("SET session_replication_role = 'origin'")) {
                stmt.execute();
            } catch (SQLException ignored) {
            }

            conn.close();
            System.out.println("  ✓ Database reset complete");

        } catch (Exception e) {
            System.out.println("  ✗ Database reset failed: " + e.getMessage());
            errors++;
        }

        // 2. Delete File Storage
        System.out.println("\n[2/4] Clearing file storage...");
        Path vaultData = Paths.get("vault_data");
        String[] directories = { "credentials", "db/credentials", "db/tokens", "db/users",
                "keys", "certificates" };

        for (String dir : directories) {
            Path dirPath = vaultData.resolve(dir);
            try {
                if (Files.exists(dirPath)) {
                    int deleted = deleteDirectoryContents(dirPath);
                    System.out.println("  ✓ Deleted " + deleted + " files from " + dir);
                }
            } catch (IOException e) {
                System.out.println("  ✗ Failed to clear " + dir + ": " + e.getMessage());
                errors++;
            }
        }

        // 3. Reset Ledger
        System.out.println("\n[3/4] Resetting ledger...");
        Path ledgerFile = Paths.get("ledger-server/data/ledger.json");
        try {
            if (Files.exists(ledgerFile)) {
                // Write empty array to reset ledger
                Files.writeString(ledgerFile, "[]", StandardCharsets.UTF_8);
                System.out.println("  ✓ Ledger reset to empty state");
            } else {
                System.out.println("  ℹ Ledger file not found (already empty)");
            }
        } catch (IOException e) {
            System.out.println("  ✗ Failed to reset ledger: " + e.getMessage());
            errors++;
        }

        // 4. Clear session
        System.out.println("\n[4/4] Clearing session...");
        try {
            authService.logout();
            System.out.println("  ✓ Session cleared");
        } catch (Exception e) {
            System.out.println("  ✗ Failed to clear session: " + e.getMessage());
            errors++;
        }

        // Final report
        System.out.println("\n" + "=".repeat(56));
        if (errors == 0) {
            System.out.println("✓ SYSTEM RESET COMPLETE");
            System.out.println("  All data has been permanently deleted.");
            System.out.println("  The system is now in initial state.");
        } else {
            System.out.println("⚠️  SYSTEM RESET COMPLETED WITH " + errors + " ERROR(S)");
            System.out.println("  Some data may not have been deleted.");
            System.out.println("  Check error messages above for details.");
        }
        System.out.println("=".repeat(56) + "\n");
    }

    // ---------------------------
    // Helper utilities
    // ---------------------------

    private long queryCount(java.sql.Connection c, String sql) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 0;
    }

    private long[] directorySizeAndCount(Path dir) throws IOException {
        if (!Files.exists(dir) || !Files.isDirectory(dir))
            return null;
        final long[] acc = new long[] { 0L, 0L };
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                if (Files.isRegularFile(p)) {
                    acc[1] += 1;
                    acc[0] += Files.size(p);
                } else if (Files.isDirectory(p)) {
                    long[] sub = directorySizeAndCount(p);
                    if (sub != null) {
                        acc[0] += sub[0];
                        acc[1] += sub[1];
                    }
                }
            }
        }
        return acc;
    }

    private int deleteDirectoryContents(Path dir) throws IOException {
        int count = 0;
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return 0;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    count += deleteDirectoryContents(entry);
                    Files.delete(entry);
                } else {
                    Files.delete(entry);
                    count++;
                }
            }
        }
        return count;
    }

    private String extractTextFromLedgerBlock(com.vaultify.models.LedgerBlock block) {
        if (block == null)
            return null;

        String[] candidates = { "getAction", "getActionName", "getPayload", "getData", "getBody", "getDetails",
                "getMeta", "getContent", "getMessage" };

        for (String mname : candidates) {
            try {
                java.lang.reflect.Method m = block.getClass().getMethod(mname);
                if (m != null) {
                    Object val = m.invoke(block);
                    if (val != null) {
                        String s = val.toString().trim();
                        if (!s.isEmpty())
                            return s;
                    }
                }
            } catch (NoSuchMethodException ignored) {
                // try next
            } catch (Throwable t) {
                // continue defensively
            }
        }

        try {
            String s = block.toString();
            return (s != null) ? s.trim() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean columnExists(java.sql.Connection conn, String table, String column) {
        try (ResultSet rs = conn.getMetaData().getColumns(null, "public", table, column)) {
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    private static class DbCred {
        String credentialId;
        long id;
        String filename;
        long fileSize;
        long userId;
        boolean isFallbackId;

        DbCred(String credentialId, long id, String filename, long fileSize, long userId, boolean isFallbackId) {
            this.credentialId = credentialId;
            this.id = id;
            this.filename = filename;
            this.fileSize = fileSize;
            this.userId = userId;
            this.isFallbackId = isFallbackId;
        }
    }
}
