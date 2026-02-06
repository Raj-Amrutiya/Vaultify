package com.vaultify.cli;

import java.util.Scanner;

import com.vaultify.cli.handlers.AuthHandler;
import com.vaultify.cli.handlers.SystemHandler;
import com.vaultify.cli.handlers.TokenHandler;
import com.vaultify.cli.handlers.VaultHandler;
import com.vaultify.service.AuthService;
import com.vaultify.service.LedgerService;
import com.vaultify.service.TokenService;
import com.vaultify.service.VaultService;
import com.vaultify.service.VerificationService;
import com.vaultify.util.Config;

/**
 * CommandRouter for Vaultify CLI.
 * Handles user commands by delegating to specific handlers.
 */
public class CommandRouter {

    // Services
    private static final AuthService authService = new AuthService();
    private static final VerificationService verificationService = new VerificationService();
    private static final VaultService vaultService = new VaultService();
    private static final TokenService tokenService = new TokenService();
    private static final LedgerService ledgerService = new LedgerService();

    // Handlers
    private static final AuthHandler authHandler = new AuthHandler(authService);
    private static final VaultHandler vaultHandler = new VaultHandler(authService, vaultService, tokenService,
            verificationService);
    private static final TokenHandler tokenHandler = new TokenHandler(authService, tokenService);
    private static final SystemHandler systemHandler = new SystemHandler(ledgerService, authService);

    // -------------------------
    // Instance entrypoint used by VaultifyApplication
    // -------------------------
    public void start() {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("vaultify> ");
                String command = scanner.nextLine().trim();
                if (command.isEmpty())
                    continue;
                handle(command, scanner);
            }
        }
    }

    // -------------------------
    // Static router (reusable)
    // -------------------------
    public static void handle(String command, Scanner scanner) {
        boolean devMode = Config.isDevMode();

        switch (command) {
            case "register" -> authHandler.register(scanner);
            case "login" -> authHandler.login(scanner);
            case "logout" -> authHandler.logout();
            case "whoami" -> authHandler.whoami();
            case "vault" -> vaultHandler.handleVaultCommand(scanner);
            case "revoke-token" -> tokenHandler.revokeToken(scanner);
            case "list-tokens" -> tokenHandler.listTokens();
            case "verify-ledger" -> systemHandler.verifyLedger();
            case "help" -> printHelp();
            case "exit" -> {
                System.out.println("Exiting Vaultify CLI...");
                System.exit(0);
            }

            // New commands (non-invasive)
            case "stats" -> systemHandler.showStats();
            case "health" -> systemHandler.showHealth();
            case "reconcile", "drift-report" -> systemHandler.reconcileAndReport(scanner);

            // Dev-only commands
            case "test-ledger" -> {
                if (devMode) {
                    systemHandler.testLedgerConnection();
                } else {
                    System.out.println("✗ Command 'test-ledger' is only available in development mode.");
                }
            }
            case "test-db" -> {
                if (devMode) {
                    systemHandler.testDatabaseConnection();
                } else {
                    System.out.println("✗ Command 'test-db' is only available in development mode.");
                }
            }
            case "reset-all", "reset" -> {
                if (devMode) {
                    systemHandler.resetAll(scanner);
                } else {
                    System.out.println("✗ Command 'reset-all' is only available in development mode.");
                }
            }
            case "dev-mode" -> systemHandler.showDevModeStatus();

            default -> System.out.println("Unknown command: " + command);
        }
    }

    // ---------------------------
    // help
    // ---------------------------

    private static void printHelp() {
        boolean devMode = Config.isDevMode();

        System.out.println("\n=== Vaultify CLI v0.1 Beta Help" + (devMode ? " [DEV MODE]" : " [PRODUCTION]") + " ===");
        System.out.println("\nCore Commands:");
        System.out.println("  register       - create a new user with RSA key pair");
        System.out.println("  login          - login with username/password");
        System.out.println("  logout         - logout current user");
        System.out.println("  whoami         - show current logged-in user");
        System.out.println("  vault          - vault operations (add/list/view/delete credentials)");
        System.out.println("  revoke-token   - revoke a previously generated token");
        System.out.println("  list-tokens    - list all tokens you've generated");
        System.out.println("  verify-ledger  - verify integrity of the blockchain ledger");

        System.out.println("\nMonitoring Commands:");
        System.out.println("  stats          - show system stats (counts, disk usage)");
        System.out.println("  health         - run health checks (DB, ledger, storage)");
        System.out.println("  reconcile      - reconcile DB, stored files and ledger; produce drift report");
        System.out.println("  drift-report   - alias for reconcile");

        if (devMode) {
            System.out.println("\n⚠️  Development Commands (dev.mode=true):");
            System.out.println("  test-ledger    - test connection to remote ledger server");
            System.out.println("  test-db        - test database connection and schema");
            System.out.println("  reset-all      - ⚠️  DELETE ALL DATA (users, credentials, tokens, ledger)");
            System.out.println("  dev-mode       - show current development mode status");
        }

        System.out.println("\nGeneral:");
        System.out.println("  help           - show this help");
        System.out.println("  exit           - quit CLI");
        System.out.println("\n================================================\n");
    }
}
