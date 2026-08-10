package com.tianshu.assets.documentrelation.application;

import com.tianshu.assets.asset.domain.Asset;
import com.tianshu.assets.asset.domain.AssetRepository;
import com.tianshu.assets.document.domain.DocumentRepository;
import com.tianshu.assets.document.domain.DocumentStatus;
import com.tianshu.assets.document.domain.KnowledgeDocument;
import com.tianshu.assets.documentrelation.domain.AssetDocumentRelation;
import com.tianshu.assets.documentrelation.domain.AssetDocumentRelationRepository;
import com.tianshu.assets.documentrelation.domain.AssetDocumentRelationType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AssetDocumentRelationService {

    private final AssetDocumentRelationRepository relations;
    private final AssetRepository assets;
    private final DocumentRepository documents;

    public AssetDocumentRelationService(AssetDocumentRelationRepository relations, AssetRepository assets,
            DocumentRepository documents) {
        this.relations = relations;
        this.assets = assets;
        this.documents = documents;
    }

    public AssetDocumentRelation create(long assetId, long documentId, AssetDocumentRelationType type, String operator) {
        requireAsset(assetId);
        requireDocument(documentId, false);
        var existing = relations.findAny(assetId, documentId, type);
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        if (existing.isPresent() && existing.get().active()) {
            throw new AssetDocumentRelationConflictException("该关联关系已存在");
        }
        if (existing.isPresent()) {
            var restored = existing.get();
            return relations.update(new AssetDocumentRelation(restored.id(), assetId, documentId, type,
                    restored.createdBy(), restored.createdAt(), operator, now, "", null, restored.version() + 1),
                    "RESTORE", operator, restored.version());
        }
        return relations.save(new AssetDocumentRelation(0, assetId, documentId, type, operator, now, "", null,
                "", null, 0), "CREATE", operator);
    }

    public AssetDocumentRelation changeType(long relationId, AssetDocumentRelationType type, String operator,
            long expectedVersion) {
        var current = requiredRelation(relationId);
        if (!current.active()) throw new AssetDocumentRelationConflictException("已解除的关联不能修改类型");
        if (current.relationType() == type) return current;
        var duplicate = relations.findAny(current.assetId(), current.documentId(), type);
        if (duplicate.isPresent() && duplicate.get().active()) {
            throw new AssetDocumentRelationConflictException("目标关系类型已存在");
        }
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return relations.update(new AssetDocumentRelation(current.id(), current.assetId(), current.documentId(), type,
                current.createdBy(), current.createdAt(), operator, now, "", null, current.version() + 1),
                "CHANGE_TYPE", operator, expectedVersion);
    }

    public void remove(long relationId, String operator, long expectedVersion) {
        var current = requiredRelation(relationId);
        if (!current.active()) return;
        var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        relations.update(new AssetDocumentRelation(current.id(), current.assetId(), current.documentId(),
                current.relationType(), current.createdBy(), current.createdAt(), operator, now, operator, now,
                current.version() + 1), "REMOVE", operator, expectedVersion);
    }

    public List<AssetDocumentRelation> byAsset(long assetId) {
        requireAsset(assetId);
        return relations.findActiveByAssetId(assetId).stream()
                .filter(relation -> documents.findById(relation.documentId())
                        .filter(document -> document.status() == DocumentStatus.PUBLISHED)
                        .isPresent())
                .toList();
    }

    public List<AssetDocumentRelation> byDocument(long documentId) {
        requireDocument(documentId, false);
        return relations.findActiveByDocumentId(documentId);
    }

    public Asset asset(long assetId) {
        return requireAsset(assetId);
    }

    public KnowledgeDocument document(long documentId) {
        return requireDocument(documentId, false);
    }

    private AssetDocumentRelation requiredRelation(long relationId) {
        return relations.findById(relationId)
                .orElseThrow(() -> new IllegalArgumentException("关联关系不存在或不可访问"));
    }

    private Asset requireAsset(long assetId) {
        return assets.findById(assetId).orElseThrow(() -> new IllegalArgumentException("关联资产不存在或不可访问"));
    }

    private KnowledgeDocument requireDocument(long documentId, boolean publishedOnly) {
        var document = documents.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("关联文档不存在或不可访问"));
        if (document.status() == DocumentStatus.DISABLED || (publishedOnly && document.status() != DocumentStatus.PUBLISHED)) {
            throw new IllegalArgumentException("关联文档不存在或不可访问");
        }
        return document;
    }
}
