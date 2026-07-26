package com.tianshu.assets.governance.acceptance.domain;

import java.util.Map;

public record GovernanceQualityPolicySnapshot(
        long id,
        String policyCode,
        long version,
        Map<GovernanceQualityMetric, Double> thresholds,
        boolean notApplicablePasses,
        boolean samplingRequired,
        int sampleSize) {

    public GovernanceQualityPolicySnapshot {
        thresholds = thresholds == null ? Map.of() : Map.copyOf(thresholds);
        if (id <= 0 || policyCode == null || policyCode.isBlank() || version <= 0) {
            throw new IllegalArgumentException("质量策略标识和版本不合法");
        }
        if (!thresholds.keySet().containsAll(java.util.Set.of(GovernanceQualityMetric.values()))
                || thresholds.values().stream().anyMatch(value -> value == null || value < 0 || value > 1)) {
            throw new IllegalArgumentException("质量指标阈值必须完整且位于 0 到 1 之间");
        }
        if ((samplingRequired && sampleSize <= 0) || (!samplingRequired && sampleSize < 0)) {
            throw new IllegalArgumentException("质量抽样数量不合法");
        }
    }

    public double threshold(GovernanceQualityMetric metric) {
        return thresholds.get(metric);
    }
}
