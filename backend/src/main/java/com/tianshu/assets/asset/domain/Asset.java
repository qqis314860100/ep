package com.tianshu.assets.asset.domain;

import java.time.Instant;
import java.util.List;

public record Asset(
        long id,
        String assetNumber,
        String name,
        String description,
        AssetType assetType,
        AssetStatus status,
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

    public Asset {
        specialties = specialties == null ? List.of() : List.copyOf(specialties);
        tags = tags == null ? List.of() : List.copyOf(tags);
        moduleTags = moduleTags == null ? List.of() : List.copyOf(moduleTags);
        linkedModuleAssetIds = linkedModuleAssetIds == null ? List.of() : List.copyOf(linkedModuleAssetIds);
        equipmentInterconnectCode = equipmentInterconnectCode == null ? "" : equipmentInterconnectCode;
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        files = files == null ? List.of() : List.copyOf(files);
    }

    public Asset(long id, String assetNumber, String name, String description, AssetType assetType,
            AssetStatus status, List<String> specialties, List<String> tags, List<AssetScope> scopes,
            List<AssetFile> files, String ownerName, String ownerDepartment, Instant updatedAt, boolean legacy) {
        this(id, assetNumber, name, description, assetType, status, specialties, tags, List.of(), false,
                List.of(), "", scopes, files, ownerName, ownerDepartment, updatedAt, legacy);
    }
}
