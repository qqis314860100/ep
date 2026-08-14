package com.tianshu.assets.asset.domain;

import java.time.Instant;

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
        String description,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt,
        long version) {

    public AssetRelation {
        if (sourceAssetId <= 0 || targetAssetId <= 0) {
            throw new IllegalArgumentException("关系双方资产 ID 必须为正数");
        }
        if (relationType == null) {
            throw new IllegalArgumentException("关系类型不能为空");
        }
    }

    /** 当前资产是否为关系的源端（正向）。 */
    public boolean fromSourceSide(long assetId) {
        return sourceAssetId == assetId;
    }
}
