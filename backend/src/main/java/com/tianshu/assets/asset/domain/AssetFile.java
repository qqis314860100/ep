package com.tianshu.assets.asset.domain;

public record AssetFile(
        long id,
        String name,
        String format,
        long sizeBytes,
        String role,
        boolean previewable,
        boolean primary,
        String storageKey,
        String contentSha256) {

    public AssetFile(long id, String name, String format, long sizeBytes, String role,
            boolean previewable, boolean primary) {
        this(id, name, format, sizeBytes, role, previewable, primary, "", "");
    }

    public AssetFile {
        storageKey = storageKey == null ? "" : storageKey;
        contentSha256 = contentSha256 == null ? "" : contentSha256;
    }
}
