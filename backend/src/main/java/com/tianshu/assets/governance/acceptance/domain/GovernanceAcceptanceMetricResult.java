package com.tianshu.assets.governance.acceptance.domain;

import java.util.List;

public record GovernanceAcceptanceMetricResult(
        long id,
        long roundId,
        GovernanceQualityMetric metric,
        int numerator,
        int denominator,
        Double value,
        double threshold,
        MetricApplicability applicability,
        boolean passed,
        List<Long> affectedItemIds,
        long version) {

    public GovernanceAcceptanceMetricResult {
        affectedItemIds = affectedItemIds == null ? List.of() : List.copyOf(affectedItemIds);
        if (metric == null || applicability == null || numerator < 0 || denominator < numerator
                || threshold < 0 || threshold > 1 || version < 0) {
            throw new IllegalArgumentException("质量指标结果不合法");
        }
        if (denominator == 0 && (value != null || applicability != MetricApplicability.NOT_APPLICABLE)
                || denominator > 0 && (value == null || applicability != MetricApplicability.APPLICABLE)) {
            throw new IllegalArgumentException("质量指标适用性与分母不一致");
        }
    }

    public enum MetricApplicability {
        APPLICABLE,
        NOT_APPLICABLE
    }
}
