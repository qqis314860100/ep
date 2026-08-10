package com.tianshu.assets.document.application;

import com.tianshu.assets.common.file.FileStorage;
import com.tianshu.assets.document.domain.DocumentFile;
import com.tianshu.assets.document.domain.DocumentRepository;
import com.tianshu.assets.document.domain.DocumentScopeMode;
import com.tianshu.assets.document.domain.DocumentStatus;
import com.tianshu.assets.document.domain.DocumentVersion;
import com.tianshu.assets.document.domain.DocumentVersionStatus;
import com.tianshu.assets.document.domain.KnowledgeDocument;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;

@Service
public class DocumentCommandService {

    private final DocumentRepository repository;
    private final FileStorage fileStorage;

    public DocumentCommandService(DocumentRepository repository, FileStorage fileStorage) {
        this.repository = repository;
        this.fileStorage = fileStorage;
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
        validateScope(command.scopeMode(), command.scopes().isEmpty());

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

    private void validateScope(DocumentScopeMode mode, boolean scopesEmpty) {
        if (mode == null || mode == DocumentScopeMode.UNCLASSIFIED) {
            throw new IllegalArgumentException("请选择文档适用方式");
        }
        if (mode == DocumentScopeMode.GLOBAL && !scopesEmpty) {
            throw new IllegalArgumentException("全局通用文档不能填写指定适用范围");
        }
        if (mode == DocumentScopeMode.SPECIFIED && scopesEmpty) {
            throw new IllegalArgumentException("指定范围文档至少需要一组适用范围");
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
