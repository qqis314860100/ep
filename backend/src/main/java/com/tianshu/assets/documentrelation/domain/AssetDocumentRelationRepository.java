package com.tianshu.assets.documentrelation.domain;

import java.util.List;
import java.util.Optional;

public interface AssetDocumentRelationRepository {

    List<AssetDocumentRelation> findActiveByAssetId(long assetId);

    List<AssetDocumentRelation> findActiveByDocumentId(long documentId);

    Optional<AssetDocumentRelation> findById(long id);

    Optional<AssetDocumentRelation> findAny(long assetId, long documentId, AssetDocumentRelationType relationType);

    AssetDocumentRelation save(AssetDocumentRelation relation, String action, String operator);

    AssetDocumentRelation update(AssetDocumentRelation relation, String action, String operator, long expectedVersion);
}
