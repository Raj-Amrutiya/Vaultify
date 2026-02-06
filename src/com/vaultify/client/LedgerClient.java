package com.vaultify.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.vaultify.models.LedgerBlock;
import com.vaultify.util.Config;
import com.vaultify.verifier.Certificate;

/**
 * HTTP client for communicating with the remote Vaultify Ledger Server.
 * This is the ONLY way to interact with the ledger - no local fallback.
 */
public class LedgerClient {
    private static final String LEDGER_API_BASE_URL = Config.get("LEDGER_API_URL",
            "https://ledger-service-rbc0.onrender.com/api");
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Gson gson = new Gson();

    // ============================================
    // LEDGER BLOCK OPERATIONS
    // ============================================

    /**
     * Append a new block to the ledger
     */
    public static LedgerBlock appendBlock(long userId, String username, String action, String dataHash) {
        return appendBlock(userId, username, action, dataHash, null, null);
    }

    public static LedgerBlock appendBlock(long userId, String username, String action, String dataHash,
            String credentialId, String token) {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("userId", userId);
            requestBody.addProperty("username", username);
            requestBody.addProperty("action", action);
            requestBody.addProperty("dataHash", dataHash);
            if (credentialId != null) {
                requestBody.addProperty("credentialId", credentialId);
            }
            if (token != null) {
                requestBody.addProperty("token", token);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LEDGER_API_BASE_URL + "/ledger/blocks"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                JsonObject responseBody = gson.fromJson(response.body(), JsonObject.class);
                JsonObject blockJson = responseBody.getAsJsonObject("block");
                return parseLedgerBlock(blockJson);
            } else {
                throw new RuntimeException("Failed to append block: " + response.body());
            }

        } catch (IOException | InterruptedException | RuntimeException e) {
            System.err.println("✗ ERROR: Could not connect to ledger server: " + e.getMessage());
            System.err.println("  Make sure the ledger server is running: npm start (in ledger-server/)");
            return null;
        }
    }

    /**
     * Get all blocks from the ledger
     */
    public static List<LedgerBlock> getAllBlocks() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LEDGER_API_BASE_URL + "/ledger/blocks"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject responseBody = gson.fromJson(response.body(), JsonObject.class);
                JsonArray blocksArray = responseBody.getAsJsonArray("blocks");

                List<LedgerBlock> blocks = new ArrayList<>();
                for (int i = 0; i < blocksArray.size(); i++) {
                    blocks.add(parseLedgerBlock(blocksArray.get(i).getAsJsonObject()));
                }
                return blocks;
            } else {
                throw new RuntimeException("Failed to get blocks: " + response.body());
            }

        } catch (IOException | InterruptedException | RuntimeException e) {
            System.err.println("✗ ERROR: Could not connect to ledger server: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get a specific block by hash
     */
    public static LedgerBlock getBlockByHash(String hash) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LEDGER_API_BASE_URL + "/ledger/blocks/" + hash))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            switch (response.statusCode()) {
                case 200 -> {
                    // Server returns block directly, not wrapped in "block" field
                    JsonObject blockJson = gson.fromJson(response.body(), JsonObject.class);
                    if (blockJson == null) {
                        System.err.println("✗ ERROR: Server returned null block");
                        return null;
                    }
                    return parseLedgerBlock(blockJson);
                }
                case 404 -> {
                    return null;
                }
                default -> {
                    System.err.println("✗ ERROR: Unexpected status " + response.statusCode() + ": " + response.body());
                    return null;
                }
            }

        } catch (IOException | InterruptedException | RuntimeException e) {
            System.err.println("✗ ERROR: Could not connect to ledger server: " + e.getMessage());
            return null;
        }
    }

    /**
     * Verify the entire ledger chain integrity
     */
    public static boolean verifyLedgerIntegrity() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LEDGER_API_BASE_URL + "/ledger/verify"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject responseBody = gson.fromJson(response.body(), JsonObject.class);
                return responseBody.get("valid").getAsBoolean();
            } else {
                return false;
            }

        } catch (JsonSyntaxException | IOException | InterruptedException e) {
            System.err.println("✗ ERROR: Could not connect to ledger server: " + e.getMessage());
            return false;
        }
    }

    // ============================================
    // CERTIFICATE OPERATIONS
    // ============================================

    /**
     * Store a certificate in the ledger server
     */
    public static void storeCertificate(Certificate certificate) {
        try {
            // Build payload matching server's expected structure
            JsonObject payload = new JsonObject();
            payload.addProperty("issuerUserId", certificate.issuerUserId);
            payload.addProperty("credentialId", certificate.credentialId);
            payload.addProperty("tokenHash", certificate.tokenHash);
            payload.addProperty("expiry", certificate.expiryEpochMs);
            payload.addProperty("ledgerBlockHash", certificate.ledgerBlockHash);

            // Build request body with certificateId, payload, signature, issuerPublicKey
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("certificateId", certificate.tokenHash); // Use tokenHash as unique ID
            requestBody.add("payload", payload);
            requestBody.addProperty("signature", certificate.signatureBase64);
            requestBody.addProperty("issuerPublicKey", certificate.issuerPublicKeyPem);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LEDGER_API_BASE_URL + "/certificates"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 201) {
                System.err.println("✗ WARNING: Certificate registration returned status " + response.statusCode()
                        + ": " + response.body());
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("✗ ERROR: Could not store certificate on ledger server: " + e.getMessage());
        }
    }

    /**
     * Get a certificate by tokenHash from the ledger server
     */
    public static Certificate getCertificate(String tokenHash) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LEDGER_API_BASE_URL + "/certificates/" + tokenHash))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            switch (response.statusCode()) {
                case 200 -> {
                    // Server returns certificate directly (not wrapped in "certificate" field)
                    JsonObject certJson = gson.fromJson(response.body(), JsonObject.class);

                    if (certJson == null) {
                        System.err.println("✗ ERROR: Server returned null certificate");
                        return null;
                    }

                    // Parse the payload structure from server
                    JsonObject payloadObj = certJson.getAsJsonObject("payload");
                    if (payloadObj == null) {
                        System.err.println("✗ ERROR: Certificate missing payload field");
                        return null;
                    }

                    Certificate cert = new Certificate();
                    cert.tokenHash = certJson.get("certificateId").getAsString();
                    cert.issuerUserId = payloadObj.get("issuerUserId").getAsLong();
                    cert.credentialId = payloadObj.get("credentialId").getAsLong();
                    cert.expiryEpochMs = payloadObj.get("expiry").getAsLong();
                    cert.ledgerBlockHash = payloadObj.get("ledgerBlockHash").getAsString();
                    cert.signatureBase64 = certJson.get("signature").getAsString();
                    cert.issuerPublicKeyPem = certJson.get("issuerPublicKey").getAsString();
                    cert.createdAtMs = certJson.has("createdAt") ? certJson.get("createdAt").getAsLong() : 0;

                    return cert;
                }
                case 404 -> {
                    return null;
                }
                default -> {
                    System.err.println("✗ ERROR: Unexpected status " + response.statusCode() + ": " + response.body());
                    return null;
                }
            }

        } catch (IOException | InterruptedException | RuntimeException e) {
            System.err.println("✗ ERROR: Could not connect to ledger server: " + e.getMessage());
            return null;
        }
    }

    // ============================================
    // TOKEN REVOCATION OPERATIONS
    // ============================================

    /**
     * Revoke a token by adding it to the revocation list
     */
    public static boolean revokeToken(String tokenHash) {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("tokenHash", tokenHash);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LEDGER_API_BASE_URL + "/tokens/revoked"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200 || response.statusCode() == 201;

        } catch (IOException | InterruptedException e) {
            System.err.println("✗ ERROR: Could not revoke token on ledger server: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a token is revoked
     */
    public static boolean isTokenRevoked(String tokenHash) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LEDGER_API_BASE_URL + "/tokens/revoked/" + tokenHash))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject responseBody = gson.fromJson(response.body(), JsonObject.class);
                // Server returns "isRevoked" field, not "revoked"
                if (responseBody.has("isRevoked")) {
                    return responseBody.get("isRevoked").getAsBoolean();
                }
                return false;
            } else {
                return false;
            }

        } catch (JsonSyntaxException | IOException | InterruptedException e) {
            System.err.println("✗ ERROR: Could not check token revocation: " + e.getMessage());
            return false;
        }
    }

    // ============================================
    // PUBLIC KEY OPERATIONS
    // ============================================

    /**
     * Register a user's public key with the ledger server
     */
    public static boolean registerPublicKey(long userId, String publicKeyPem) {
        try {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("publicKey", publicKeyPem);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LEDGER_API_BASE_URL + "/users/" + userId + "/public-key"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200 || response.statusCode() == 201;

        } catch (IOException | InterruptedException e) {
            System.err.println("✗ ERROR: Could not register public key: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get a user's public key from the ledger server
     */
    public static String getPublicKey(long userId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LEDGER_API_BASE_URL + "/users/" + userId + "/public-key"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject responseBody = gson.fromJson(response.body(), JsonObject.class);
                return responseBody.get("publicKey").getAsString();
            } else {
                return null;
            }

        } catch (JsonSyntaxException | IOException | InterruptedException e) {
            System.err.println("✗ ERROR: Could not get public key: " + e.getMessage());
            return null;
        }
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    /**
     * Check if ledger server is reachable
     */
    public static boolean isServerAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LEDGER_API_BASE_URL.replace("/api", "") + "/api/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200;

        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static LedgerBlock parseLedgerBlock(JsonObject blockJson) {
        int index = blockJson.get("index").getAsInt();
        long timestamp = blockJson.get("timestamp").getAsLong();
        String action = blockJson.get("action").getAsString();
        String dataHash = blockJson.get("dataHash").getAsString();
        String prevHash = blockJson.get("prevHash").getAsString();
        String hash = blockJson.get("hash").getAsString();

        return new LedgerBlock(index, timestamp, action, dataHash, prevHash, hash);
    }
}
