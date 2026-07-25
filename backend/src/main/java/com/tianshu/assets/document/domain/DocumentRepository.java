package com.tianshu.assets.document.domain;

import java.util.Optional;

public interface DocumentRepository {

    DocumentPage searchPublished(DocumentSearchCriteria criteria);

    Optional<KnowledgeDocument> findById(long id);

    boolean existsByDocumentNumber(String documentNumber);

    KnowledgeDocument save(KnowledgeDocument document);

    KnowledgeDocument update(KnowledgeDocument document, long expectedVersion);
}
