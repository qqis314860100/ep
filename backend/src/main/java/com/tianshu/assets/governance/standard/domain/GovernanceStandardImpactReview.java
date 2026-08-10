package com.tianshu.assets.governance.standard.domain;

import java.time.Instant;
import java.util.List;

public record GovernanceStandardImpactReview(
        long id,
        long standardId,
        long affectedAssetCount,
        List<Long> assetIds,
        Status status,
        Instant createdAt) {

    public GovernanceStandardImpactReview {
        assetIds = assetIds == null ? List.of() : List.copyOf(assetIds);
        if (affectedAssetCount != assetIds.size()) {
            throw new IllegalArgumentException("影响资产数量与复核清单不一致");
        }
    }

    public enum Status {
        OPEN,
        COMPLETED
    }
}
