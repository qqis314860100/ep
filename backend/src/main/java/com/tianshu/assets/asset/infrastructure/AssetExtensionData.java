package com.tianshu.assets.asset.infrastructure;

import com.tianshu.assets.asset.domain.AssetScope;
import java.util.List;

record AssetExtensionData(
        List<String> moduleTags,
        boolean standardEquipmentModule,
        List<Long> linkedModuleAssetIds,
        String equipmentInterconnectCode,
        List<AssetScope> scopes) {
}
