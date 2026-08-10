package com.tianshu.assets.document.domain;

import java.time.Instant;
import java.util.List;

public record KnowledgeDocument(
        long id,
        String documentNumber,
        String title,
        String summary,
        String categoryCode,
        String maintainerId,
        String maintainerName,
        String maintainerDepartment,
        DocumentScopeMode scopeMode,
        List<DocumentScope> scopes,
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
        scopeMode = scopeMode == null ? DocumentScopeMode.UNCLASSIFIED : scopeMode;
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }

    public KnowledgeDocument(long id, String documentNumber, String title, String summary, String categoryCode,
            String maintainerId, String maintainerName, String maintainerDepartment, DocumentStatus status,
            Long currentVersionId, DocumentVersion currentVersion, Instant createdAt, Instant updatedAt, long version) {
        this(id, documentNumber, title, summary, categoryCode, maintainerId, maintainerName, maintainerDepartment,
                DocumentScopeMode.UNCLASSIFIED, List.of(), status, currentVersionId, currentVersion, createdAt,
                updatedAt, version);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
