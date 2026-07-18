package com.tianshu.assets.asset.application;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface AssetFileStorage {

    String store(InputStream content, long size, String originalFilename, String contentType) throws IOException;

    Optional<StoredAssetFile> open(String storageKey);

    record StoredAssetFile(
            String storageKey,
            String originalFilename,
            String contentType,
            long size,
            String sha256,
            byte[] content) {
    }
}
