package com.tianshu.assets.document.application;

import com.tianshu.assets.common.file.FileStorage;
import com.tianshu.assets.document.domain.DocumentFile;
import com.tianshu.assets.document.domain.DocumentPage;
import com.tianshu.assets.document.domain.DocumentRepository;
import com.tianshu.assets.document.domain.DocumentSearchCriteria;
import com.tianshu.assets.document.domain.DocumentStatus;
import com.tianshu.assets.document.domain.DocumentVersion;
import com.tianshu.assets.document.domain.DocumentVersionStatus;
import com.tianshu.assets.document.domain.KnowledgeDocument;
import java.util.List;
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
                .filter(document -> document.status() == DocumentStatus.PUBLISHED
                        || document.status() == DocumentStatus.DISABLED)
                .orElseThrow(() -> new DocumentNotFoundException("文档不存在或不可访问"));
    }

    /** 历史版本清单（DOC-VERSION-03），仅已发布文档。 */
    public List<DocumentVersion> listVersions(long documentId) {
        getPublished(documentId);
        return repository.findVersions(documentId);
    }

    public DocumentFileAccess openPublishedFile(long documentId, long versionId, long fileId) {
        getPublished(documentId);
        var version = repository.findVersion(documentId, versionId)
                .filter(item -> item.status() == DocumentVersionStatus.PUBLISHED)
                .orElseThrow(() -> new DocumentNotFoundException("文档版本不存在或不可访问"));
        var file = version.files().stream()
                .filter(item -> item.id() == fileId)
                .findFirst()
                .orElseThrow(() -> new DocumentNotFoundException("文档文件不存在或不可访问"));
        var stored = fileStorage.open(file.storageKey())
                .orElseThrow(() -> new DocumentNotFoundException("文档文件不存在或不可访问"));
        return new DocumentFileAccess(documentById(documentId), file, stored);
    }

    private KnowledgeDocument documentById(long documentId) {
        return repository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("文档不存在或不可访问"));
    }

    public record DocumentFileAccess(
            KnowledgeDocument document,
            DocumentFile file,
            FileStorage.StoredFile storedFile) {
    }
}
