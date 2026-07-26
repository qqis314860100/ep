package com.tianshu.assets.governance.execution.domain;

import com.tianshu.assets.governance.issue.domain.GovernanceField;

public record GovernanceItem(
        long id,
        long taskId,
        long planId,
        long issueId,
        long assetId,
        GovernanceField targetField,
        String actionType,
        String responsibleUserId,
        GovernanceItemStatus status,
        long assetVersion,
        int governanceRound,
        String scopeFingerprint,
        long version,
        Long currentResultVersionId,
        String blockReason,
        Long reworkSourceItemId) {

    public GovernanceItem {
        if (targetField == null || status == null) throw new IllegalArgumentException("治理项类型和状态不能为空");
        if (actionType == null || actionType.isBlank()) throw new IllegalArgumentException("治理动作不能为空");
        if (responsibleUserId == null || responsibleUserId.isBlank()) {
            throw new IllegalArgumentException("治理项责任人不能为空");
        }
        if (governanceRound <= 0 || version < 0) throw new IllegalArgumentException("治理项版本不合法");
    }
}
