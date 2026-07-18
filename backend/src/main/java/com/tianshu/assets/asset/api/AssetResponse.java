package com.tianshu.assets.asset.api;

import com.tianshu.assets.asset.domain.Asset;
import com.tianshu.assets.asset.domain.AssetFile;
import com.tianshu.assets.asset.domain.AssetScope;
import java.time.Instant;
import java.util.List;

public record AssetResponse(
        long id,
        String assetNumber,
        String name,
        String description,
        String assetType,
        String status,
        List<String> specialties,
        List<String> tags,
        List<String> moduleTags,
        boolean standardEquipmentModule,
        List<Long> linkedModuleAssetIds,
        String equipmentInterconnectCode,
        List<AssetScope> scopes,
        List<AssetFile> files,
        String ownerName,
        String ownerDepartment,
        Instant updatedAt,
        boolean legacy) {

    public static AssetResponse from(Asset asset) {
        return new AssetResponse(
                asset.id(),
                asset.assetNumber(),
                asset.name(),
                asset.description(),
                asset.assetType().name(),
                asset.status().name(),
                asset.specialties(),
                asset.tags(),
                asset.moduleTags(),
                asset.standardEquipmentModule(),
                asset.linkedModuleAssetIds(),
                asset.equipmentInterconnectCode(),
                asset.scopes(),
                asset.files(),
                asset.ownerName(),
                asset.ownerDepartment(),
                asset.updatedAt(),
                asset.legacy());
    }
}
