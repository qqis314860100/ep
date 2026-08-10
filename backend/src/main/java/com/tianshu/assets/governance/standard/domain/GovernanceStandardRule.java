package com.tianshu.assets.governance.standard.domain;

public record GovernanceStandardRule(
        String targetField,
        GovernanceStandardRuleType ruleType,
        String description,
        boolean blocking,
        String configurationJson) {

    public GovernanceStandardRule {
        if (targetField == null || targetField.isBlank()) {
            throw new IllegalArgumentException("标准规则目标字段不能为空");
        }
        if (ruleType == null) {
            throw new IllegalArgumentException("标准规则类型不能为空");
        }
        description = description == null ? "" : description.trim();
        configurationJson = configurationJson == null || configurationJson.isBlank()
                ? "{}" : configurationJson.trim();
    }
}
