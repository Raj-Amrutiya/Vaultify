package com.vaultify.repository;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vaultify.models.Token;

public class FileTokenRepository implements TokenRepository {
    private static final String DIR = "vault_data/db/tokens";
    private final Path basePath = Paths.get(DIR);
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public FileTokenRepository() {
        // Disable file-based repository usage
        throw new RepositoryException("FileTokenRepository is disabled. Use JDBC/PostgresTokenRepository.");
    }

    private Path fileFor(String token) {
        return basePath.resolve(token + ".json");
    }

    @Override
    public Token save(Token token) {
        try (FileWriter fw = new FileWriter(fileFor(token.getToken()).toFile())) {
            gson.toJson(token, fw);
            return token;
        } catch (IOException e) {
            throw new RepositoryException("Failed to save token file", e);
        }
    }

    @Override
    public Token findByTokenString(String tokenString) {
        Path p = fileFor(tokenString);
        if (!Files.exists(p))
            return null;
        try (FileReader fr = new FileReader(p.toFile())) {
            return gson.fromJson(fr, Token.class);
        } catch (IOException e) {
            throw new RepositoryException("Failed reading token file", e);
        }
    }

    @Override
    public List<Token> findByIssuerUserId(long userId) {
        List<Token> list = new ArrayList<>();
        try {
            Files.list(basePath).filter(f -> f.getFileName().toString().endsWith(".json")).forEach(f -> {
                try (FileReader fr = new FileReader(f.toFile())) {
                    Token t = gson.fromJson(fr, Token.class);
                    if (t != null && t.getIssuerUserId() == userId)
                        list.add(t);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            throw new RepositoryException("Failed listing token files", e);
        }
        return list;
    }

    @Override
    public void revoke(String tokenString) {
        Token t = findByTokenString(tokenString);
        if (t != null) {
            t.setRevoked(true);
            save(t);
        }
    }

    @Override
    public void deleteExpired() {
        try {
            Files.list(basePath).filter(f -> f.getFileName().toString().endsWith(".json")).forEach(f -> {
                try (FileReader fr = new FileReader(f.toFile())) {
                    Token t = gson.fromJson(fr, Token.class);
                    if (t != null && t.isExpired() && !t.isRevoked()) {
                        Files.deleteIfExists(f);
                    }
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            throw new RepositoryException("Failed deleting expired token files", e);
        }
    }
}
