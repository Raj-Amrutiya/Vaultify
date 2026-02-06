package com.vaultify.cli.handlers;

import java.util.List;
import java.util.Scanner;

import com.vaultify.models.Token;
import com.vaultify.models.User;
import com.vaultify.service.AuthService;
import com.vaultify.service.TokenService;

public class TokenHandler {
    private final AuthService authService;
    private final TokenService tokenService;

    public TokenHandler(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    public void revokeToken(Scanner scanner) {
        if (!authService.isLoggedIn()) {
            System.out.println("Please login first.");
            return;
        }

        try {
            System.out.print("Enter token to revoke: ");
            String tokenString = scanner.nextLine().trim();

            // Validate token exists and belongs to user
            Token token = tokenService.validateToken(tokenString);
            if (token == null) {
                System.out.println("✗ Token not found or already expired.");
                return;
            }

            User user = authService.getCurrentUser();
            if (token.getIssuerUserId() != user.getId()) {
                System.out.println("✗ You can only revoke tokens you issued.");
                return;
            }

            tokenService.revokeToken(tokenString);

        } catch (Exception e) {
            System.out.println("✗ Error revoking token: " + e.getMessage());
        }
    }

    public void listTokens() {
        if (!authService.isLoggedIn()) {
            System.out.println("Please login first.");
            return;
        }

        try {
            User user = authService.getCurrentUser();
            List<Token> tokens = tokenService.listUserTokens(user.getId());

            if (tokens.isEmpty()) {
                System.out.println("No tokens generated.");
                return;
            }

            System.out.println("\n=== Your Generated Tokens ===");
            for (Token t : tokens) {
                System.out.println("\nToken: " + t.getToken());
                System.out.println("  Credential ID: " + t.getCredentialId());
                System.out.println("  Expires: " + t.getExpiry());
                System.out.println(
                        "  Status: " + (t.isValid() ? "✓ Valid" : (t.isRevoked() ? "✗ Revoked" : "✗ Expired")));
            }
            System.out.println("\nTotal: " + tokens.size() + " token(s)");

        } catch (Exception e) {
            System.out.println("✗ Error listing tokens: " + e.getMessage());
        }
    }
}
