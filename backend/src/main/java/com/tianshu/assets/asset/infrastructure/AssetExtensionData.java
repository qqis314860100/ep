package com.tianshu.assets.asset.infrastructure;

import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.asset.domain.AssetFile;
import com.tianshu.assets.asset.domain.AssetStatus;
import com.tianshu.assets.asset.domain.AssetType;
import java.util.List;

record AssetExtensionData(
        String assetNumber,
        AssetType assetType,
        AssetStatus status,
        List<String> moduleTags,
        boolean standardEquipmentModule,
        List<Long> linkedModuleAssetIds,
        String equipmentInterconnectCode,
        String ownerDepartment,
        List<AssetScope> scopes,
        List<AssetFile> files) {
}
