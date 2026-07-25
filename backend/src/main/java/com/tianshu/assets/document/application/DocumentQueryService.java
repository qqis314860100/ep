package com.tianshu.assets.document.application;

import com.tianshu.assets.common.file.FileStorage;
import com.tianshu.assets.document.domain.DocumentFile;
import com.tianshu.assets.document.domain.DocumentPage;
import com.tianshu.assets.document.domain.DocumentRepository;
import com.tianshu.assets.document.domain.DocumentSearchCriteria;
import com.tianshu.assets.document.domain.DocumentStatus;
import com.tianshu.assets.document.domain.KnowledgeDocument;
import org.springframework.stereotype.Service;

@Service
public class DocumentQueryService {

    private final DocumentRepository repository;
    private final FileStorage fileStorage;

    public DocumentQueryService(DocumentRepository repository, FileStorage fileStorage) {
        this.repository = repository;
        this.fileStorage = fileStorage;
    }

    public DocumentPage search(DocumentSearchCriteria criteria) {
        return repository.searchPublished(criteria);
    }

    public KnowledgeDocument getPublished(long documentId) {
        return repository.findById(documentId)
                .filter(document -> document.status() == DocumentStatus.PUBLISHED)
                .orElseThrow(() -> new DocumentNotFoundException("文档不存在或不可访问"));
    }

    public DocumentFileAccess openPublishedFile(long documentId, long versionId, long fileId) {
        var document = getPublished(documentId);
        if (document.currentVersionId() == null || document.currentVersionId() != versionId
                || document.currentVersion().id() != versionId) {
            throw new DocumentNotFoundException("文档版本不存在或不可访问");
        }
        var file = document.currentVersion().files().stream()
                .filter(item -> item.id() == fileId)
                .findFirst()
                .orElseThrow(() -> new DocumentNotFoundException("文档文件不存在或不可访问"));
        var stored = fileStorage.open(file.storageKey())
                .orElseThrow(() -> new DocumentNotFoundException("文档文件不存在或不可访问"));
        return new DocumentFileAccess(document, file, stored);
    }

    public record DocumentFileAccess(
            KnowledgeDocument document,
            DocumentFile file,
            FileStorage.StoredFile storedFile) {
    }
}
