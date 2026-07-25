package com.tianshu.assets.document.domain;

public record DocumentFile(
        long id,
        String name,
        String format,
        long sizeBytes,
        boolean previewable,
        String storageKey,
        String contentSha256) {

    public DocumentFile {
        name = text(name);
        format = text(format);
        storageKey = text(storageKey);
        contentSha256 = text(contentSha256);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
