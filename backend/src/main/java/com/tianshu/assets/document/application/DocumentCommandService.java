package com.tianshu.assets.document.application;

import com.tianshu.assets.common.file.FileStorage;
import com.tianshu.assets.document.domain.DocumentFile;
import com.tianshu.assets.document.domain.DocumentRepository;
import com.tianshu.assets.document.domain.DocumentScopeMode;
import com.tianshu.assets.document.domain.DocumentScope;
import com.tianshu.assets.document.domain.DocumentStatus;
import com.tianshu.assets.document.domain.DocumentVersion;
import com.tianshu.assets.document.domain.DocumentVersionStatus;
import com.tianshu.assets.document.domain.KnowledgeDocument;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DocumentCommandService {

    private static final java.util.Set<String> GOVERNANCE_ROLES = java.util.Set.of("CONTENT_ADMIN", "SYSTEM_ADMIN");

    private final DocumentRepository repository;
    private final FileStorage fileStorage;
    private final com.tianshu.assets.system.domain.OperationLogStore operationLogs;

    public DocumentCommandService(DocumentRepository repository, FileStorage fileStorage) {
        this(repository, fileStorage, new com.tianshu.assets.system.infrastructure.InMemoryOperationLogStore());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DocumentCommandService(DocumentRepository repository, FileStorage fileStorage,
            com.tianshu.assets.system.domain.OperationLogStore operationLogs) {
        this.repository = repository;
        this.fileStorage = fileStorage;
        this.operationLogs = operationLogs;
    }

    /** 停用文档（DOC-06）：原因必填、管理员操作、默认隐藏于普通检索、历史全部保留。 */
    public KnowledgeDocument disable(long documentId, String reason, String operatorUserId,
            String operatorName, String roles) {
        if (roles == null || java.util.Arrays.stream(roles.split(","))
                .map(value -> value.trim().toUpperCase(java.util.Locale.ROOT))
                .noneMatch(GOVERNANCE_ROLES::contains)) {
            throw new com.tianshu.assets.asset.application.ForbiddenOperationException("仅内容管理员或系统管理员可以停用文档");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("停用原因不能为空");
        }
        var document = repository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("文档不存在"));
        if (document.status() != DocumentStatus.PUBLISHED) {
            throw new DocumentStateConflictException("只有已发布文档可以停用");
        }
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var disabled = new KnowledgeDocument(document.id(), document.documentNumber(), document.title(),
                document.summary(), document.categoryCode(), document.maintainerId(), document.maintainerName(),
                document.maintainerDepartment(), document.scopeMode(), document.scopes(), DocumentStatus.DISABLED,
                document.currentVersionId(), document.currentVersion(), document.createdAt(), now, document.version() + 1);
        try {
            var saved = repository.update(disabled, document.version());
            appendDisableAudit(saved, reason, operatorUserId, operatorName);
            return saved;
        } catch (IllegalStateException exception) {
            throw new DocumentStateConflictException("文档已被其他用户更新，请刷新后重试", exception);
        }
    }

    private void appendDisableAudit(KnowledgeDocument document, String reason, String operatorUserId,
            String operatorName) {
        try {
            operationLogs.append(new com.tianshu.assets.system.domain.OperationLog(0, operatorUserId,
                    "DOCUMENT_DISABLE", "DOCUMENT", document.id(),
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of(
                            "reason", reason,
                            "operatorName", operatorName == null ? "" : operatorName,
                            "toStatus", DocumentStatus.DISABLED.name())),
                    Instant.now()));
        } catch (Exception exception) {
            throw new IllegalStateException("停用审计写入失败", exception);
        }
    }

    public KnowledgeDocument createDraft(CreateDocumentDraftCommand command) {
        var number = text(command.documentNumber());
        if (!number.isBlank() && repository.existsByDocumentNumber(number)) {
            throw new DuplicateDocumentNumberException("文档编号已存在：" + number);
        }
        require(text(command.title()), "文档标题不能为空");
        require(text(command.summary()), "文档摘要不能为空");
        require(text(command.categoryCode()), "文档分类不能为空");
        require(text(command.maintainerName()), "维护人不能为空");
        if (command.files().isEmpty()) {
            throw new IllegalArgumentException("请至少上传一个文件");
        }
        command.files().forEach(this::validateDraftFile);
        validateScope(command.scopeMode(), command.scopes());

        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var version = new DocumentVersion(0, 0, defaultText(command.versionNumber(), "V1.0"),
                defaultText(command.changeSummary(), "首次发布"), DocumentVersionStatus.DRAFT,
                command.files(), text(command.maintainerName()), now, "", null);
        var draft = new KnowledgeDocument(0, number, command.title(), command.summary(), command.categoryCode(),
                command.maintainerId(), command.maintainerName(), command.maintainerDepartment(),
                command.scopeMode(), command.scopes(), DocumentStatus.DRAFT, null, version, now, now, 0);
        return repository.save(draft);
    }

    public KnowledgeDocument publish(long documentId, String publisherId, String publisherName) {
        var draft = repository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("文档不存在"));
        if (draft.status() != DocumentStatus.DRAFT || draft.currentVersion().status() != DocumentVersionStatus.DRAFT) {
            throw new DocumentStateConflictException("只有草稿文档可以首次发布");
        }
        validateForPublish(draft);
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var publishedVersion = new DocumentVersion(draft.currentVersion().id(), draft.id(),
                draft.currentVersion().versionNumber(), draft.currentVersion().changeSummary(),
                DocumentVersionStatus.PUBLISHED, draft.currentVersion().files(), draft.currentVersion().createdBy(),
                draft.currentVersion().createdAt(), defaultText(publisherName, publisherId), now);
        var published = new KnowledgeDocument(draft.id(), draft.documentNumber(), draft.title(), draft.summary(),
                draft.categoryCode(), draft.maintainerId(), draft.maintainerName(), draft.maintainerDepartment(),
                draft.scopeMode(), draft.scopes(), DocumentStatus.PUBLISHED, publishedVersion.id(), publishedVersion, draft.createdAt(), now,
                draft.version() + 1);
        try {
            return repository.update(published, draft.version());
        } catch (IllegalStateException exception) {
            throw new DocumentStateConflictException("文档已被其他用户更新，请刷新后重试", exception);
        }
    }

    /** 创建新版本草稿（DOC-VERSION-01）：仅已发布文档，版本号唯一，至少一个文件。 */
    public DocumentVersion createVersionDraft(CreateVersionDraftCommand command) {
        var document = repository.findById(command.documentId())
                .orElseThrow(() -> new DocumentNotFoundException("文档不存在"));
        if (document.status() != DocumentStatus.PUBLISHED) {
            throw new DocumentStateConflictException("只有已发布文档可以创建新版本");
        }
        var versionNumber = text(command.versionNumber());
        require(versionNumber, "版本号不能为空");
        require(text(command.changeSummary()), "变更说明不能为空");
        if (command.files().isEmpty()) {
            throw new IllegalArgumentException("新版本至少需要一个文件");
        }
        command.files().forEach(this::validateDraftFile);
        var duplicated = repository.findVersions(document.id()).stream()
                .anyMatch(version -> version.versionNumber().equalsIgnoreCase(versionNumber));
        if (duplicated) {
            throw new DocumentStateConflictException("版本号已存在：" + versionNumber);
        }
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var draft = new DocumentVersion(0, document.id(), versionNumber, text(command.changeSummary()),
                DocumentVersionStatus.DRAFT, command.files(), document.maintainerName(), now, "", null);
        return repository.saveVersion(draft);
    }

    /** 发布新版本（DOC-VERSION-02）：成功后替换当前有效版本，原版本转为历史。 */
    public KnowledgeDocument publishVersion(long documentId, long versionId, String publisherId, String publisherName) {
        var document = repository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("文档不存在"));
        if (document.status() != DocumentStatus.PUBLISHED) {
            throw new DocumentStateConflictException("只有已发布文档可以发布新版本");
        }
        var version = repository.findVersion(documentId, versionId)
                .orElseThrow(() -> new DocumentNotFoundException("版本不存在"));
        if (version.status() != DocumentVersionStatus.DRAFT) {
            throw new DocumentStateConflictException("只有草稿版本可以发布");
        }
        if (version.files().isEmpty() || version.files().stream()
                .anyMatch(file -> file.storageKey().isBlank() || fileStorage.open(file.storageKey()).isEmpty())) {
            throw new DocumentPublishValidationException("版本文件不存在或尚未完成上传");
        }
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var publishedVersion = new DocumentVersion(version.id(), documentId, version.versionNumber(),
                version.changeSummary(), DocumentVersionStatus.PUBLISHED, version.files(), version.createdBy(),
                version.createdAt(), defaultText(publisherName, publisherId), now);
        repository.saveVersion(publishedVersion);
        var updated = new KnowledgeDocument(document.id(), document.documentNumber(), document.title(),
                document.summary(), document.categoryCode(), document.maintainerId(), document.maintainerName(),
                document.maintainerDepartment(), document.scopeMode(), document.scopes(), DocumentStatus.PUBLISHED,
                publishedVersion.id(), publishedVersion, document.createdAt(), now, document.version() + 1);
        try {
            return repository.update(updated, document.version());
        } catch (IllegalStateException exception) {
            throw new DocumentStateConflictException("文档已被其他用户更新，请刷新后重试", exception);
        }
    }

    public record CreateVersionDraftCommand(
            long documentId,
            String versionNumber,
            String changeSummary,
            List<DocumentFile> files) {}

    private void validateForPublish(KnowledgeDocument document) {
        if (document.documentNumber().isBlank() || document.title().isBlank() || document.summary().isBlank()
                || document.categoryCode().isBlank() || document.maintainerName().isBlank()
                || document.currentVersion().versionNumber().isBlank() || document.currentVersion().files().isEmpty()) {
            throw new DocumentPublishValidationException("文档发布信息不完整");
        }
        for (var file : document.currentVersion().files()) {
            if (file.storageKey().isBlank() || fileStorage.open(file.storageKey()).isEmpty()) {
                throw new DocumentPublishValidationException("文档文件不存在或尚未完成上传：" + file.name());
            }
        }
    }

    private void validateDraftFile(DocumentFile file) {
        require(file.name(), "文件名不能为空");
        require(file.format(), "文件格式不能为空");
        if (file.sizeBytes() < 0) throw new IllegalArgumentException("文件大小不能为负数");
    }

    private void validateScope(DocumentScopeMode mode, java.util.List<DocumentScope> scopes) {
        if (mode == null || mode == DocumentScopeMode.UNCLASSIFIED) {
            throw new IllegalArgumentException("请选择文档适用方式");
        }
        if (mode == DocumentScopeMode.GLOBAL && !scopes.isEmpty()) {
            throw new IllegalArgumentException("全局通用文档不能填写指定适用范围");
        }
        if (mode == DocumentScopeMode.SPECIFIED && scopes.isEmpty()) {
            throw new IllegalArgumentException("指定范围文档至少需要一组适用范围");
        }
        for (var scope : scopes) {
            if (scope.platformFamily().isBlank() || scope.productLine().isBlank() || scope.baseName().isBlank()
                    || scope.productionLine().isBlank()) {
                throw new IllegalArgumentException("指定范围必须填写平台族、蓝本、基地和拉线");
            }
        }
    }

    private void require(String value, String message) {
        if (value.isBlank()) throw new IllegalArgumentException(message);
    }

    private String defaultText(String value, String fallback) {
        var normalized = text(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
