package com.tianshu.assets.common.file;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface FileStorage {

    String store(InputStream content, long size, String originalFilename, String contentType) throws IOException;

    Optional<StoredFile> open(String storageKey);

    record StoredFile(
            String storageKey,
            String originalFilename,
            String contentType,
            long size,
            String sha256,
            byte[] content) {
    }
}
