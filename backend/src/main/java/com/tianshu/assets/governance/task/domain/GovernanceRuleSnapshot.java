package com.tianshu.assets.governance.task.domain;

import java.util.Map;

public record GovernanceRuleSnapshot(
        long id,
        String dataStandardId,
        long dataStandardVersion,
        long fieldRuleVersion,
        Map<String, Long> dictionaryVersions,
        String qualityPolicyId,
        long qualityPolicyVersion) {

    public GovernanceRuleSnapshot {
        if (dataStandardId == null || dataStandardId.isBlank()) {
            throw new IllegalArgumentException("启用数据标准不能为空");
        }
        if (dataStandardVersion <= 0 || fieldRuleVersion <= 0 || qualityPolicyVersion <= 0) {
            throw new IllegalArgumentException("治理规则版本不合法");
        }
        if (qualityPolicyId == null || qualityPolicyId.isBlank()) {
            throw new IllegalArgumentException("质量策略不能为空");
        }
        dictionaryVersions = dictionaryVersions == null ? Map.of() : Map.copyOf(dictionaryVersions);
    }

    public GovernanceRuleSnapshot withId(long snapshotId) {
        return new GovernanceRuleSnapshot(
                snapshotId, dataStandardId, dataStandardVersion, fieldRuleVersion,
                dictionaryVersions, qualityPolicyId, qualityPolicyVersion);
    }
}
