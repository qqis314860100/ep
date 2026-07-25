package com.tianshu.assets.document.domain;

import java.time.Instant;

public record KnowledgeDocument(
        long id,
        String documentNumber,
        String title,
        String summary,
        String categoryCode,
        String maintainerId,
        String maintainerName,
        String maintainerDepartment,
        DocumentStatus status,
        Long currentVersionId,
        DocumentVersion currentVersion,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public KnowledgeDocument {
        documentNumber = text(documentNumber);
        title = text(title);
        summary = text(summary);
        categoryCode = text(categoryCode);
        maintainerId = text(maintainerId);
        maintainerName = text(maintainerName);
        maintainerDepartment = text(maintainerDepartment);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
