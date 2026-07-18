package com.tianshu.assets.asset.api;

import com.tianshu.assets.asset.domain.AssetRelation;

public record AssetRelationResponse(
        long id,
        long sourceAssetId,
        long targetAssetId,
        String targetAssetNumber,
        String targetAssetName,
        String targetAssetType,
        String targetAssetStatus,
        String relationType,
        String directionLabel,
        String primaryScope,
        String description) {

    public static AssetRelationResponse from(AssetRelation relation) {
        return new AssetRelationResponse(
                relation.id(),
                relation.sourceAssetId(),
                relation.targetAssetId(),
                relation.targetAssetNumber(),
                relation.targetAssetName(),
                relation.targetAssetType().name(),
                relation.targetAssetStatus().name(),
                relation.relationType().name(),
                relation.directionLabel(),
                relation.primaryScope(),
                relation.description());
    }
}
