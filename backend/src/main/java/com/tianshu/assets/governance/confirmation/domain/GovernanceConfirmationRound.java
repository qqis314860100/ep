package com.tianshu.assets.governance.confirmation.domain;

import java.time.Instant;
import java.util.Map;

public record GovernanceConfirmationRound(
        long id,
        long taskId,
        int governanceRound,
        Map<Long, Long> resultVersionIds,
        Status status,
        Instant createdAt,
        Instant completedAt,
        long version) {

    public GovernanceConfirmationRound {
        resultVersionIds = Map.copyOf(resultVersionIds);
        if (taskId <= 0 || governanceRound <= 0 || resultVersionIds.isEmpty()) {
            throw new IllegalArgumentException("确认轮次范围不能为空");
        }
        if (status == null || createdAt == null) {
            throw new IllegalArgumentException("确认轮次状态和创建时间不能为空");
        }
    }

    public enum Status {
        PENDING,
        COMPLETED
    }
}
