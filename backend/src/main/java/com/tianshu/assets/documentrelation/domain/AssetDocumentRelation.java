package com.tianshu.assets.documentrelation.domain;

import java.time.Instant;

public record AssetDocumentRelation(
        long id,
        long assetId,
        long documentId,
        AssetDocumentRelationType relationType,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt,
        String deletedBy,
        Instant deletedAt,
        long version) {

    public boolean active() {
        return deletedAt == null;
    }
}
