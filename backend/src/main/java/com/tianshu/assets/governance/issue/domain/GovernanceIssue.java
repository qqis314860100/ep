package com.tianshu.assets.governance.issue.domain;

import java.time.Instant;

public record GovernanceIssue(
        long id,
        long assetId,
        GovernanceField targetField,
        String issueType,
        String targetPath,
        String ruleCode,
        long ruleVersion,
        String originalFactJson,
        long assetVersion,
        String scopeFingerprint,
        String severity,
        boolean blocking,
        GovernanceIssueStatus status,
        Long taskId,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public GovernanceIssue {
        if (assetId <= 0) throw new IllegalArgumentException("资产 ID 不合法");
        if (targetField == null) throw new IllegalArgumentException("目标字段不能为空");
        if (issueType == null || issueType.isBlank()) throw new IllegalArgumentException("问题类型不能为空");
        if (targetPath == null || targetPath.isBlank()) throw new IllegalArgumentException("目标路径不能为空");
        if (ruleCode == null || ruleCode.isBlank()) throw new IllegalArgumentException("规则编码不能为空");
        if (ruleVersion <= 0) throw new IllegalArgumentException("规则版本不合法");
        if (severity == null || severity.isBlank()) throw new IllegalArgumentException("问题严重度不能为空");
        if (status == null) throw new IllegalArgumentException("问题状态不能为空");
        if (assetVersion < 0 || version < 0) throw new IllegalArgumentException("问题版本不合法");
    }

    public String fingerprint() {
        return assetId + "|" + issueType + "|" + targetPath + "|" + ruleVersion;
    }

    public GovernanceIssue claim(long claimedTaskId, Instant updatedAt) {
        if (status != GovernanceIssueStatus.OPEN) {
            throw new IllegalStateException("仅开放问题可以纳入治理任务");
        }
        return new GovernanceIssue(
                id, assetId, targetField, issueType, targetPath, ruleCode, ruleVersion,
                originalFactJson, assetVersion, scopeFingerprint, severity, blocking,
                GovernanceIssueStatus.CLAIMED, claimedTaskId, version + 1, createdAt, updatedAt);
    }
}
