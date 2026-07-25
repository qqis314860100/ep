package com.tianshu.assets.document.api;

import com.tianshu.assets.document.domain.DocumentFile;
import com.tianshu.assets.document.domain.DocumentStatus;
import com.tianshu.assets.document.domain.DocumentVersion;
import com.tianshu.assets.document.domain.DocumentVersionStatus;
import com.tianshu.assets.document.domain.KnowledgeDocument;
import java.time.Instant;
import java.util.List;

public record DocumentResponse(
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
        VersionResponse currentVersion,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public static DocumentResponse from(KnowledgeDocument document) {
        return new DocumentResponse(document.id(), document.documentNumber(), document.title(), document.summary(),
                document.categoryCode(), document.maintainerId(), document.maintainerName(),
                document.maintainerDepartment(), document.status(), document.currentVersionId(),
                VersionResponse.from(document.currentVersion()), document.createdAt(), document.updatedAt(),
                document.version());
    }

    public record VersionResponse(
            long id,
            long documentId,
            String versionNumber,
            String changeSummary,
            DocumentVersionStatus status,
            List<FileResponse> files,
            String createdBy,
            Instant createdAt,
            String publishedBy,
            Instant publishedAt) {

        static VersionResponse from(DocumentVersion version) {
            return new VersionResponse(version.id(), version.documentId(), version.versionNumber(),
                    version.changeSummary(), version.status(), version.files().stream().map(FileResponse::from).toList(),
                    version.createdBy(), version.createdAt(), version.publishedBy(), version.publishedAt());
        }
    }

    public record FileResponse(
            long id,
            String name,
            String format,
            long sizeBytes,
            boolean previewable,
            String contentSha256) {

        static FileResponse from(DocumentFile file) {
            return new FileResponse(file.id(), file.name(), file.format(), file.sizeBytes(), file.previewable(),
                    file.contentSha256());
        }
    }
}
