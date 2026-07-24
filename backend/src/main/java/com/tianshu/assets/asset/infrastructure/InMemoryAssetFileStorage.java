package com.tianshu.assets.asset.infrastructure;

import com.tianshu.assets.asset.application.AssetFileStorage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class InMemoryAssetFileStorage implements AssetFileStorage {

    private static final long MAX_FILE_SIZE = 500L * 1024 * 1024;
    private final Map<String, StoredAssetFile> files = new ConcurrentHashMap<>();

    @Override
    public String store(InputStream content, long size, String originalFilename, String contentType) throws IOException {
        if (size > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("单个文件不能超过 500 MB");
        }
        var bytes = readLimited(content, MAX_FILE_SIZE);
        var key = UUID.randomUUID().toString();
        var sha256 = sha256(bytes);
        files.put(key, new StoredAssetFile(key, originalFilename, contentType, bytes.length, sha256, bytes));
        return key;
    }

    @Override
    public Optional<StoredAssetFile> open(String storageKey) {
        return Optional.ofNullable(files.get(storageKey));
    }

    private byte[] readLimited(InputStream content, long maxSize) throws IOException {
        var output = new ByteArrayOutputStream();
        var buffer = new byte[8192];
        var total = 0L;
        int read;
        while ((read = content.read(buffer)) != -1) {
            total += read;
            if (total > maxSize) {
                throw new IllegalArgumentException("单个文件不能超过 500 MB");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }
}
