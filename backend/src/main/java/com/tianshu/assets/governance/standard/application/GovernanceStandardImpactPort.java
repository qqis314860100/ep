package com.tianshu.assets.governance.standard.application;

import com.tianshu.assets.asset.domain.AssetType;
import java.util.List;

public interface GovernanceStandardImpactPort {

    List<Long> findPotentiallyAffectedAssetIds(List<AssetType> applicableAssetTypes);
}
