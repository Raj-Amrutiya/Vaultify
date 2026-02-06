package com.vaultify.cli.handlers;

import java.util.Scanner;

import com.vaultify.models.User;
import com.vaultify.service.AuthService;

public class AuthHandler {
    private final AuthService authService;

    public AuthHandler(AuthService authService) {
        this.authService = authService;
    }

    public void register(Scanner scanner) {
        System.out.print("Enter username: ");
        String username = scanner.nextLine().trim();
        if (username.isEmpty()) {
            System.out.println("Username cannot be empty.");
            return;
        }

        System.out.print("Enter password: ");
        String password = scanner.nextLine();
        if (password.isEmpty()) {
            System.out.println("Password cannot be empty.");
            return;
        }

        System.out.print("Confirm password: ");
        String confirmPassword = scanner.nextLine();
        if (!password.equals(confirmPassword)) {
            System.out.println("Passwords do not match.");
            return;
        }

        try {
            User user = authService.register(username, password);
            if (user == null) {
                System.out.println("Username already exists.");
                return;
            }
            System.out.println("User '" + username + "' registered successfully.");
            System.out.println("RSA key pair generated and private key encrypted.");
        } catch (Exception e) {
            System.out.println("Registration failed: " + e.getMessage());
        }
    }

    public void login(Scanner scanner) {
        if (authService.isLoggedIn()) {
            System.out.println("Already logged in as: " + authService.getCurrentUser().getUsername());
            return;
        }

        System.out.print("Username: ");
        String username = scanner.nextLine().trim();

        System.out.print("Password: ");
        String password = scanner.nextLine();

        boolean success = authService.login(username, password);
        if (success) {
            System.out.println("Login successful. Welcome, " + username + "!");
        } else {
            System.out.println("Invalid credentials.");
        }
    }

    public void logout() {
        if (!authService.isLoggedIn()) {
            System.out.println("No user is currently logged in.");
            return;
        }
        String username = authService.getCurrentUser().getUsername();
        authService.logout();
        System.out.println("User '" + username + "' logged out successfully.");
    }

    public void whoami() {
        if (!authService.isLoggedIn()) {
            System.out.println("No user is currently logged in.");
            return;
        }
        User user = authService.getCurrentUser();
        System.out.println("Logged in as: " + user.getUsername());
        System.out.println("User ID: " + user.getId());
        System.out.println("User Public Key:\n" + user.getPublicKey());
    }
}
