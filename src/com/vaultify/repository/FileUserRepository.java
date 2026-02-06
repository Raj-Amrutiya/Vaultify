package com.vaultify.repository;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vaultify.models.User;

/**
 * File-based implementation of UserRepository storing each user as JSON by
 * username.
 * ID is not persisted separately; assumes DB is authoritative for numeric id.
 */
public class FileUserRepository implements UserRepository {
    private static final String USER_DIR = "vault_data/db/users";
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path basePath = Paths.get(USER_DIR);

    public FileUserRepository() {
        // Disable file-based repository usage
        throw new RepositoryException("FileUserRepository is disabled. Use JDBC/PostgresUserRepository.");
    }

    private Path fileFor(String username) {
        return basePath.resolve(username + ".json");
    }

    @Override
    public User save(User user) {
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        }
        try (FileWriter fw = new FileWriter(fileFor(user.getUsername()).toFile())) {
            gson.toJson(user, fw);
            return user;
        } catch (IOException e) {
            throw new RepositoryException("Failed to save user file", e);
        }
    }

    @Override
    public User findById(long id) {
        // File storage keyed by username only; cannot lookup by id efficiently.
        // Returning null encourages caller to fallback to primary repository.
        return null;
    }

    @Override
    public User findByUsername(String username) {
        Path p = fileFor(username);
        if (!Files.exists(p))
            return null;
        try (FileReader fr = new FileReader(p.toFile())) {
            return gson.fromJson(fr, User.class);
        } catch (IOException e) {
            throw new RepositoryException("Failed to read user file", e);
        }
    }

    @Override
    public List<User> findAll() {
        List<User> list = new ArrayList<>();
        try {
            Files.list(basePath).filter(f -> f.getFileName().toString().endsWith(".json")).forEach(f -> {
                try (FileReader fr = new FileReader(f.toFile())) {
                    User u = gson.fromJson(fr, User.class);
                    if (u != null)
                        list.add(u);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            throw new RepositoryException("Failed listing user files", e);
        }
        return list;
    }

    @Override
    public void delete(long id) {
        // Not supported due to lack of id indexing; noop
    }
}
