package com.tianshu.assets.asset.domain;

public record AssetRelation(
        long id,
        long sourceAssetId,
        long targetAssetId,
        String targetAssetNumber,
        String targetAssetName,
        AssetType targetAssetType,
        AssetStatus targetAssetStatus,
        RelationType relationType,
        String directionLabel,
        String primaryScope,
        String description) {}
