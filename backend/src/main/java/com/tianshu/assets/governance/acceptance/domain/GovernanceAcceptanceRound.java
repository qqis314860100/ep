package com.tianshu.assets.governance.acceptance.domain;

import java.time.Instant;
import java.util.List;

public record GovernanceAcceptanceRound(
        long id,
        long taskId,
        int governanceRound,
        GovernanceQualityPolicySnapshot policy,
        List<GovernanceAcceptanceMetricResult> metricResults,
        List<GovernanceAcceptanceSample> samples,
        Status status,
        Instant createdAt,
        Instant completedAt,
        long version) {

    public GovernanceAcceptanceRound {
        metricResults = metricResults == null ? List.of() : List.copyOf(metricResults);
        samples = samples == null ? List.of() : List.copyOf(samples);
        if (taskId <= 0 || governanceRound <= 0 || policy == null || status == null
                || createdAt == null || version < 0) {
            throw new IllegalArgumentException("验收轮次不合法");
        }
        if (metricResults.size() != GovernanceQualityMetric.values().length) {
            throw new IllegalArgumentException("验收轮次必须包含全部质量指标");
        }
    }

    public enum Status {
        OPEN,
        PASSED,
        FAILED
    }
}
