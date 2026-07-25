package com.tianshu.assets.common.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalFileStorage implements FileStorage {

    private static final long MAX_FILE_SIZE = 500L * 1024 * 1024;
    private final Path root;

    public LocalFileStorage(@Value("${asset.file-storage-directory:.data/files}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public String store(InputStream content, long size, String originalFilename, String contentType) throws IOException {
        if (size > MAX_FILE_SIZE) throw new IllegalArgumentException("单个文件不能超过 500 MB");
        Files.createDirectories(root);
        var bytes = content.readNBytes((int) Math.min(MAX_FILE_SIZE + 1, Integer.MAX_VALUE));
        if (bytes.length > MAX_FILE_SIZE) throw new IllegalArgumentException("单个文件不能超过 500 MB");
        var key = UUID.randomUUID().toString();
        Files.write(root.resolve(key + ".bin"), bytes);
        Files.writeString(root.resolve(key + ".meta"), safe(originalFilename) + "\n" + safe(contentType) + "\n" + sha256(bytes));
        return key;
    }

    @Override
    public Optional<StoredFile> open(String storageKey) {
        if (storageKey == null || !storageKey.matches("[a-fA-F0-9-]{36}")) return Optional.empty();
        try {
            var contentPath = root.resolve(storageKey + ".bin");
            var metadataPath = root.resolve(storageKey + ".meta");
            if (!Files.isRegularFile(contentPath) || !Files.isRegularFile(metadataPath)) return Optional.empty();
            var metadata = Files.readAllLines(metadataPath);
            var bytes = Files.readAllBytes(contentPath);
            return Optional.of(new StoredFile(storageKey, metadata.get(0), metadata.get(1), bytes.length,
                    metadata.size() > 2 ? metadata.get(2) : sha256(bytes), bytes));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\n", "").replace("\r", "");
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }
}
