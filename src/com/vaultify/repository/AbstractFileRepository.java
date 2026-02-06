package com.vaultify.repository;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import java.util.function.Function;

/**
 * Basic JSON file repository abstraction. Concrete classes define folder +
 * serialization.
 */
public abstract class AbstractFileRepository<T> {

    protected abstract Path basePath();

    protected abstract String filePrefix();

    protected abstract String fileExtension();

    protected abstract String serialize(T entity);

    protected abstract T deserialize(String json);

    protected abstract UUID extractId(T entity);

    protected Path resolvePath(UUID id) {
        return basePath().resolve(filePrefix() + id + fileExtension());
    }

    public void save(T entity) {
        UUID id = extractId(entity);
        Path p = resolvePath(id);
        try {
            Files.createDirectories(basePath());
            Files.writeString(p, serialize(entity), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RepositoryException("Failed to save file entity: " + p, e);
        }
    }

    public T findById(UUID id) {
        Path p = resolvePath(id);
        if (!Files.exists(p))
            return null;
        try {
            String json = Files.readString(p, StandardCharsets.UTF_8);
            return deserialize(json);
        } catch (IOException e) {
            throw new RepositoryException("Failed to read file entity: " + p, e);
        }
    }

    public void deleteById(UUID id) {
        Path p = resolvePath(id);
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            throw new RepositoryException("Failed to delete file entity: " + p, e);
        }
    }

    protected <R> R streamFiles(Function<StreamContext, R> fn) {
        try {
            Files.createDirectories(basePath());
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(basePath(),
                    filePrefix() + "*" + fileExtension())) {
                return fn.apply(new StreamContext(ds));
            }
        } catch (IOException e) {
            throw new RepositoryException("Failed streaming folder: " + basePath(), e);
        }
    }

    public static class StreamContext implements Iterable<Path> {
        private final DirectoryStream<Path> delegate;

        StreamContext(DirectoryStream<Path> delegate) {
            this.delegate = delegate;
        }

        @Override
        public java.util.Iterator<Path> iterator() {
            return delegate.iterator();
        }
    }
}
