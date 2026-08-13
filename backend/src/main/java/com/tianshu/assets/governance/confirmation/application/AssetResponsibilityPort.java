package com.tianshu.assets.governance.confirmation.application;

import java.util.Optional;

public interface AssetResponsibilityPort {

    Optional<AssetResponsibility> currentResponsibility(long assetId);

    AssetResponsibility assign(long assetId, String responsibleUserId, String responsibilityScope);

    record AssetResponsibility(long assetId, String responsibleUserId, String responsibilityScope) {
        public AssetResponsibility {
            if (assetId <= 0 || responsibleUserId == null || responsibleUserId.isBlank()
                    || responsibilityScope == null || responsibilityScope.isBlank()) {
                throw new IllegalArgumentException("资产责任关系不完整");
            }
        }
    }
}
