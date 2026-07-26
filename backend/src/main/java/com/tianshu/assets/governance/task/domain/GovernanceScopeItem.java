package com.tianshu.assets.governance.task.domain;

import com.tianshu.assets.governance.issue.domain.GovernanceField;

public record GovernanceScopeItem(
        long snapshotId,
        long taskId,
        long planId,
        long issueId,
        long assetId,
        GovernanceField targetField,
        String targetPath,
        String originalFactJson,
        long assetVersion,
        long ruleVersion,
        String scopeFingerprint,
        String responsibleUserId) {

    public GovernanceScopeItem {
        if (targetField == null) throw new IllegalArgumentException("目标字段不能为空");
        if (targetPath == null || targetPath.isBlank()) throw new IllegalArgumentException("目标路径不能为空");
        if (responsibleUserId == null || responsibleUserId.isBlank()) {
            throw new IllegalArgumentException("治理项责任人不能为空");
        }
    }
}
