package com.vaultify.cli.handlers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.util.List;
import java.util.Scanner;

import com.vaultify.models.CredentialMetadata;
import com.vaultify.models.Token;
import com.vaultify.models.User;
import com.vaultify.service.AuthService;
import com.vaultify.service.TokenService;
import com.vaultify.service.VaultService;
import com.vaultify.service.VerificationService;
import com.vaultify.util.PathValidator;
import com.vaultify.verifier.CertificateVerifier;

public class VaultHandler {
    private final AuthService authService;
    private final VaultService vaultService;
    private final TokenService tokenService;
    private final VerificationService verificationService;

    public VaultHandler(AuthService authService, VaultService vaultService, TokenService tokenService,
            VerificationService verificationService) {
        this.authService = authService;
        this.vaultService = vaultService;
        this.tokenService = tokenService;
        this.verificationService = verificationService;
    }

    public void handleVaultCommand(Scanner scanner) {
        if (!authService.isLoggedIn()) {
            System.out.println("Please login first to access vault.");
            return;
        }
        System.out.println("Entering Vault subsystem. Type 'help' for vault commands, 'back' to return.");
        while (true) {
            System.out.print("vaultify:vault> ");
            String line = scanner.nextLine().trim();
            if (line.isEmpty())
                continue;

            String[] parts = line.split("\\s+");
            String cmd = parts[0].toLowerCase();

            switch (cmd) {
                case "help" -> printVaultHelp();
                case "add" -> {
                    System.out.println("Add credential:");
                    System.out.println("  1. File");
                    System.out.println("  2. Text/Password");
                    System.out.print("Choice [1-2]: ");
                    String choice = scanner.nextLine().trim();
                    switch (choice) {
                        case "1" -> {
                            System.out.print("Enter file path: ");
                            String filePath = scanner.nextLine().trim();
                            addCredentialFromFile(filePath, scanner);
                        }
                        case "2" -> addCredentialFromText(scanner);
                        default -> System.out.println("Invalid choice.");
                    }
                }
                case "list" -> listCredentials();
                case "view" -> {
                    String id = parts.length > 1 ? parts[1] : null;
                    if (id == null || id.isEmpty()) {
                        System.out.print("Enter credential id to view: ");
                        id = scanner.nextLine().trim();
                    }
                    viewCredential(id, scanner);
                }
                case "share" -> shareCredential(scanner);
                case "delete" -> {
                    String id = parts.length > 1 ? parts[1] : null;
                    if (id == null || id.isEmpty()) {
                        System.out.print("Enter credential id to delete: ");
                        id = scanner.nextLine().trim();
                    }
                    deleteCredential(id);
                }

                case "verify-cert" -> verifyCertificate(scanner);

                case "back" -> {
                    System.out.println("Exiting Vault subsystem.");
                    return;
                }
                default -> System.out.println("Unknown vault command: " + cmd + " (type 'help' for vault commands)");
            }
        }
    }

    private void printVaultHelp() {
        System.out.println("Vault commands:");
        System.out.println("  add                   - add credential (interactive)");
        System.out.println("  delete <id>           - delete a credential");
        System.out.println("  list                  - list stored credentials");
        System.out.println("  view <id>             - view credential details");
        System.out.println("  share                 - generate share token + signed certificate for credential");
        System.out.println("  verify-cert           - verify a certificate file with public key");
        System.out.println("  back                  - return to top-level CLI");
    }

    private void addCredentialFromFile(String filePath, Scanner scanner) {
        try {
            // Validate file path
            PathValidator.ValidationResult validation = PathValidator.validateFilePath(filePath);
            if (!validation.valid) {
                System.out.println("✗ Validation failed: " + validation.message);
                return;
            }

            // Get user's public key
            User user = authService.getCurrentUser();
            PublicKey publicKey = authService.getUserPublicKey(user.getUsername());
            if (publicKey == null) {
                System.out.println("✗ Failed to load user's public key.");
                return;
            }

            // Show file info
            long fileSize = Files.size(validation.normalizedPath);
            System.out.println("\nFile: " + validation.normalizedPath.getFileName());
            System.out.println("Size: " + PathValidator.formatSize(fileSize));
            System.out.print("\nEncrypt and store this file? [y/N]: ");
            String confirm = scanner.nextLine().trim().toLowerCase();
            if (!confirm.equals("y") && !confirm.equals("yes")) {
                System.out.println("Cancelled.");
                return;
            }

            // Add credential
            String credentialId = vaultService.addCredential(user.getId(), validation.normalizedPath, publicKey);
            System.out.println("\n✓ Credential added successfully!");
            System.out.println("  ID: " + credentialId);

        } catch (Exception e) {
            System.out.println("✗ Failed to add credential: " + e.getMessage());
            System.err.println("Error: Could not encrypt or store the file. Please check file permissions.");
        }
    }

    private void addCredentialFromText(Scanner scanner) {
        try {
            System.out.println("\nEnter credential text (press Ctrl+D or Ctrl+Z when done):");
            System.out.println("Examples:");
            System.out.println("  - Password format: username: xyz, app/site: example.com, password: xyz123");
            System.out.println("  - Simple text: Any text you want to encrypt\n");

            StringBuilder text = new StringBuilder();
            System.out.print("> ");
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                // Check for end marker (empty line after content)
                if (line.equals("--end") || line.equals(".")) {
                    break;
                }
                if (!text.isEmpty()) {
                    text.append("\n");
                }
                text.append(line);

                // Single line mode - if line is not empty and we have content, ask if done
                if (!text.isEmpty()) {
                    System.out.print("Continue? [y/N] or type '--end' to finish: ");
                    String cont = scanner.nextLine().trim().toLowerCase();
                    if (!cont.equals("y") && !cont.equals("yes")) {
                        if (cont.equals("--end") || cont.equals(".")) {
                            break;
                        }
                        // User wants to finish with current content
                        break;
                    }
                    System.out.print("> ");
                }
            }

            if (text.isEmpty()) {
                System.out.println("No text entered.");
                return;
            }

            // Create temporary file
            Path tempFile = Files.createTempFile("vaultify-text-", ".txt");
            Files.writeString(tempFile, text.toString());

            // Get user's public key
            User user = authService.getCurrentUser();
            PublicKey publicKey = authService.getUserPublicKey(user.getUsername());
            if (publicKey == null) {
                System.out.println("✗ Failed to load user's public key.");
                Files.deleteIfExists(tempFile);
                return;
            }

            // Add credential
            String credentialId = vaultService.addCredential(user.getId(), tempFile, publicKey);

            // Clean up temp file
            Files.deleteIfExists(tempFile);

            System.out.println("\n✓ Text credential added successfully!");
            System.out.println("  ID: " + credentialId);

        } catch (Exception e) {
            System.out.println("✗ Failed to add text credential: " + e.getMessage());
            System.err.println("Error: Could not encrypt or store the text. Please try again.");
        }
    }

    private void listCredentials() {
        try {
            User user = authService.getCurrentUser();
            List<CredentialMetadata> credentials = vaultService.listCredentials(user.getId());

            if (credentials.isEmpty()) {
                System.out.println("No credentials stored.");
                return;
            }

            System.out.println("\n=== Your Credentials ===");
            for (CredentialMetadata meta : credentials) {
                System.out.println("\nID: " + meta.credentialIdString);
                System.out.println("  File: " + meta.filename);
                System.out.println("  Size: " + PathValidator.formatSize(meta.fileSize));
                System.out.println("  Added: " + new java.util.Date(meta.timestamp));
            }
            System.out.println("\nTotal: " + credentials.size() + " credential(s)");

        } catch (Exception e) {
            System.out.println("✗ Failed to list credentials: " + e.getMessage());
        }
    }

    private void viewCredential(String id, Scanner scanner) {
        if (id == null || id.isEmpty()) {
            System.out.println("Invalid credential id.");
            return;
        }

        try {
            User user = authService.getCurrentUser();

            // Get user's private key
            java.security.PrivateKey privateKey = authService.getUserPrivateKey(user.getUsername());
            if (privateKey == null) {
                System.out.println("✗ Failed to load user's private key.");
                return;
            }

            // Retrieve and decrypt
            byte[] plaintext = vaultService.retrieveCredential(id, privateKey);

            // Display as text (assuming text content)
            System.out.println("\n=== Credential Content ===");
            System.out.println(new String(plaintext, StandardCharsets.UTF_8));
            System.out.println("\n==========================");

            // Ask if user wants to save to file
            System.out.print("\nSave to file? [y/N]: ");
            String save = scanner.nextLine().trim().toLowerCase();
            if (save.equals("y") || save.equals("yes")) {
                System.out.print("Output file path: ");
                String outPath = scanner.nextLine().trim();
                Files.write(Paths.get(outPath), plaintext);
                System.out.println("✓ Saved to: " + outPath);
            }

        } catch (Exception e) {
            System.out.println("✗ Failed to retrieve credential: " + e.getMessage());
        }
    }

    private void deleteCredential(String id) {
        if (id == null || id.isEmpty()) {
            System.out.println("Invalid credential id.");
            return;
        }

        try {
            User user = authService.getCurrentUser();
            vaultService.deleteCredential(id, user.getId());
            System.out.println("✓ Credential deleted: " + id);

        } catch (Exception e) {
            System.out.println("✗ Failed to delete credential: " + e.getMessage());
        }
    }

    private void shareCredential(Scanner scanner) {
        if (!authService.isLoggedIn()) {
            System.out.println("Please login first.");
            return;
        }

        try {
            User user = authService.getCurrentUser();

            // Get credential ID to share
            System.out.print("Enter credential ID to share: ");
            String credentialId = scanner.nextLine().trim();

            // Validate credential exists and belongs to user
            List<CredentialMetadata> userCreds = vaultService.listCredentials(user.getId());
            CredentialMetadata cred = userCreds.stream()
                    .filter(c -> c.credentialIdString.equals(credentialId))
                    .findFirst()
                    .orElse(null);

            if (cred == null) {
                System.out.println("✗ Credential not found or you don't own it.");
                return;
            }

            System.out.print("Expiry in hours (default 48): ");
            String exp = scanner.nextLine().trim();
            int expiryHours = exp.isEmpty() ? 48 : Integer.parseInt(exp);

            // Generate token (not yet persisted) then persist
            // Use credential ID or hash of UUID string as numeric reference
            long credIdNum = (cred.id != 0) ? cred.id : Math.abs(cred.credentialIdString.hashCode());
            Token token = tokenService.generateToken(user.getId(), credIdNum, expiryHours);
            tokenService.persistToken(token);

            // Get user's private key for signing certificate
            java.security.PrivateKey privateKey = authService.getUserPrivateKey(user.getUsername());
            if (privateKey == null) {
                System.out.println("✗ Failed to load your private key.");
                return;
            }

            // Get user's public key path for certificate (auto-create if missing)
            Path publicKeyPath = Paths.get("vault_data", "keys", user.getUsername() + "_public.pem");
            if (!Files.exists(publicKeyPath)) {
                try {
                    Files.createDirectories(publicKeyPath.getParent());
                    // Reconstruct PEM from Base64 stored in user (X.509 encoded bytes)
                    byte[] pubDer = java.util.Base64.getDecoder().decode(user.getPublicKey());
                    String pemBody = java.util.Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pubDer);
                    String pem = "-----BEGIN PUBLIC KEY-----\n" + pemBody + "\n-----END PUBLIC KEY-----\n";
                    Files.writeString(publicKeyPath, pem);
                    System.out.println("[Auto-Recovery] Public key file recreated: " + publicKeyPath);
                } catch (Exception pkEx) {
                    System.out.println("✗ Public key not found and could not be recreated: " + pkEx.getMessage());
                    return;
                }
            }

            // Generate certificate
            Path certOutputDir = Paths.get("vault_data/certificates");
            Files.createDirectories(certOutputDir);
            String tokenHash = com.vaultify.crypto.HashUtil.sha256(token.getToken());
            Path certPath = certOutputDir.resolve("cert-" + tokenHash.substring(0, 16) + ".json");

            tokenService.createCertificate(token, cred, privateKey, publicKeyPath, certPath);

            System.out.println("\n✅ Share token and certificate generated!");
            System.out.println("===========================================");
            System.out.println("📤 SHARE THESE WITH RECIPIENT:");
            System.out.println("   1. Token: " + token.getToken());
            System.out.println("   2. Certificate: " + certPath.toAbsolutePath());
            System.out.println("===========================================");
            System.out.println("📋 Details:");
            System.out.println("   Credential ID: " + cred.credentialIdString);
            System.out.println("   Token Hash: " + tokenHash);
            System.out.println("   Expires: " + new java.util.Date(token.getExpiry().getTime()));
            System.out.println("===========================================");
            System.out.println("\n⚠️  SECURITY NOTES:");
            System.out.println("   • Token is sent ONCE to recipient (confidential)");
            System.out.println("   • Certificate is publicly verifiable");
            System.out.println("   • Only tokenHash stored on ledger server");
            System.out.println("===========================================\n");

        } catch (NumberFormatException nfe) {
            System.out.println("✗ Invalid numeric input.");
        } catch (Exception e) {
            System.out.println("✗ Error generating share token: " + e.getMessage());
        }
    }

    private void verifyCertificate(Scanner scanner) {
        try {
            System.out.print("Enter certificate path: ");
            Path certPath = Paths.get(scanner.nextLine().trim());

            System.out.print("Enter token (the one User A gave you): ");
            String token = scanner.nextLine().trim();

            CertificateVerifier.Result res = verificationService.verifyCertificate(certPath, token);

            if (!res.valid) {
                System.out.println("\n❌ Verification failed: " + res.message);
            }
            // Success message already printed by verifier

        } catch (Exception e) {
            System.out.println("\n✗ Error verifying certificate: " + e.getMessage());
        }
    }
}
