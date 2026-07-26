package com.tianshu.assets.governance.acceptance.application;

import com.tianshu.assets.asset.domain.AssetStatus;
import com.tianshu.assets.governance.issue.domain.GovernanceField;

public interface GovernanceAssetPort {

    GovernanceAssetSnapshot snapshot(long assetId);

    ApplyOutcome applyFieldResult(
            long itemId,
            long assetId,
            GovernanceField field,
            String proposedValueJson,
            long expectedAssetVersion,
            String actorUserId);

    boolean meetsAllActiveStandards(long assetId);

    void markStandardized(long assetId, long expectedAssetVersion, String actorUserId);

    record GovernanceAssetSnapshot(long assetId, AssetStatus status, long version) {}

    record ApplyOutcome(long assetVersion, String changeSummary) {}
}
