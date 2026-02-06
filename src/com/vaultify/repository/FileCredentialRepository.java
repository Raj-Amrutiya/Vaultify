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
import com.vaultify.models.CredentialMetadata;
import com.vaultify.models.CredentialType;

/**
 * File-based CredentialRepository storing each metadata entry as JSON.
 */
public class FileCredentialRepository implements CredentialRepository {
    private static final String DIR = "vault_data/db/credentials";
    private final Path basePath = Paths.get(DIR);
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public FileCredentialRepository() {
        // Disable file-based repository usage
        throw new RepositoryException("FileCredentialRepository is disabled. Use JDBC/PostgresCredentialRepository.");
    }

    private Path fileFor(String id) {
        return basePath.resolve(id + ".json");
    }

    @Override
    public long save(CredentialMetadata metadata, long userId) {
        if (metadata.credentialIdString == null)
            throw new RepositoryException("Missing credentialIdString for save");
        metadata.userId = userId;
        try (FileWriter fw = new FileWriter(fileFor(metadata.credentialIdString).toFile())) {
            gson.toJson(metadata, fw);
            return metadata.id > 0 ? metadata.id : -1L;
        } catch (IOException e) {
            throw new RepositoryException("Failed to save credential metadata file", e);
        }
    }

    @Override
    public CredentialMetadata findByCredentialId(String credentialId) {
        Path p = fileFor(credentialId);
        if (!Files.exists(p))
            return null;
        try (FileReader fr = new FileReader(p.toFile())) {
            CredentialMetadata meta = gson.fromJson(fr, CredentialMetadata.class);
            if (meta != null) {
                meta.credentialIdString = credentialId;
                if (meta.type == null)
                    meta.type = CredentialType.FILE;
            }
            return meta;
        } catch (IOException e) {
            throw new RepositoryException("Failed reading credential metadata file", e);
        }
    }

    @Override
    public List<CredentialMetadata> findByUserId(long userId) {
        List<CredentialMetadata> list = new ArrayList<>();
        try {
            Files.list(basePath).filter(f -> f.getFileName().toString().endsWith(".json")).forEach(f -> {
                try (FileReader fr = new FileReader(f.toFile())) {
                    CredentialMetadata meta = gson.fromJson(fr, CredentialMetadata.class);
                    if (meta != null && meta.userId == userId) {
                        if (meta.type == null)
                            meta.type = CredentialType.FILE;
                        String name = f.getFileName().toString();
                        meta.credentialIdString = name.substring(0, name.length() - 5);
                        list.add(meta);
                    }
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            throw new RepositoryException("Failed listing credential metadata files", e);
        }
        return list;
    }

    @Override
    public void deleteByCredentialId(String credentialId) {
        try {
            Files.deleteIfExists(fileFor(credentialId));
        } catch (IOException e) {
            throw new RepositoryException("Failed deleting credential metadata file", e);
        }
    }
}
