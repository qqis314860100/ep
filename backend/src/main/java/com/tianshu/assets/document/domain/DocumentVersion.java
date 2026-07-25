package com.tianshu.assets.document.domain;

import java.time.Instant;
import java.util.List;

public record DocumentVersion(
        long id,
        long documentId,
        String versionNumber,
        String changeSummary,
        DocumentVersionStatus status,
        List<DocumentFile> files,
        String createdBy,
        Instant createdAt,
        String publishedBy,
        Instant publishedAt) {

    public DocumentVersion {
        versionNumber = text(versionNumber);
        changeSummary = text(changeSummary);
        files = files == null ? List.of() : List.copyOf(files);
        createdBy = text(createdBy);
        publishedBy = text(publishedBy);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
