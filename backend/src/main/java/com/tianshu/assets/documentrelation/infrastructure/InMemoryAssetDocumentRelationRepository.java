package com.tianshu.assets.documentrelation.infrastructure;

import com.tianshu.assets.documentrelation.application.AssetDocumentRelationConflictException;
import com.tianshu.assets.documentrelation.domain.AssetDocumentRelation;
import com.tianshu.assets.documentrelation.domain.AssetDocumentRelationRepository;
import com.tianshu.assets.documentrelation.domain.AssetDocumentRelationType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("dev")
public class InMemoryAssetDocumentRelationRepository implements AssetDocumentRelationRepository {

    private final AtomicLong nextId = new AtomicLong(1);
    private final List<AssetDocumentRelation> relations = new ArrayList<>();

    @Override
    public synchronized List<AssetDocumentRelation> findActiveByAssetId(long assetId) {
        return relations.stream().filter(relation -> relation.assetId() == assetId && relation.active())
                .sorted(Comparator.comparing(AssetDocumentRelation::createdAt).reversed()).toList();
    }

    @Override
    public synchronized List<AssetDocumentRelation> findActiveByDocumentId(long documentId) {
        return relations.stream().filter(relation -> relation.documentId() == documentId && relation.active())
                .sorted(Comparator.comparing(AssetDocumentRelation::createdAt).reversed()).toList();
    }

    @Override
    public synchronized Optional<AssetDocumentRelation> findById(long id) {
        return relations.stream().filter(relation -> relation.id() == id).findFirst();
    }

    @Override
    public synchronized Optional<AssetDocumentRelation> findAny(long assetId, long documentId,
            AssetDocumentRelationType relationType) {
        return relations.stream().filter(relation -> relation.assetId() == assetId && relation.documentId() == documentId
                && relation.relationType() == relationType).findFirst();
    }

    @Override
    public synchronized AssetDocumentRelation save(AssetDocumentRelation relation, String action, String operator) {
        var saved = new AssetDocumentRelation(nextId.getAndIncrement(), relation.assetId(), relation.documentId(),
                relation.relationType(), relation.createdBy(), relation.createdAt(), relation.updatedBy(),
                relation.updatedAt(), relation.deletedBy(), relation.deletedAt(), relation.version());
        relations.add(saved);
        return saved;
    }

    @Override
    public synchronized AssetDocumentRelation update(AssetDocumentRelation relation, String action, String operator,
            long expectedVersion) {
        for (var index = 0; index < relations.size(); index++) {
            var existing = relations.get(index);
            if (existing.id() == relation.id()) {
                if (existing.version() != expectedVersion) {
                    throw new AssetDocumentRelationConflictException("关联关系已被其他用户修改，请刷新后重试");
                }
                relations.set(index, relation);
                return relation;
            }
        }
        throw new IllegalArgumentException("关联关系不存在或不可访问");
    }
}
