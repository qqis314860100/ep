package com.tianshu.assets.document.domain;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository {

    DocumentPage searchPublished(DocumentSearchCriteria criteria);

    Optional<KnowledgeDocument> findById(long id);

    boolean existsByDocumentNumber(String documentNumber);

    KnowledgeDocument save(KnowledgeDocument document);

    KnowledgeDocument update(KnowledgeDocument document, long expectedVersion);

    List<DocumentVersion> findVersions(long documentId);

    Optional<DocumentVersion> findVersion(long documentId, long versionId);

    DocumentVersion saveVersion(DocumentVersion version);
}
