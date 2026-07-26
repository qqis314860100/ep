package com.tianshu.assets.governance.acceptance.domain;

import java.time.Instant;
import java.util.List;

public record GovernanceOperationJob(
        long id,
        long taskId,
        long acceptanceRoundId,
        List<GovernanceOperationJobItem> items,
        String requestedBy,
        Instant requestedAt,
        Status status,
        long version) {

    public GovernanceOperationJob {
        if (taskId <= 0 || acceptanceRoundId <= 0 || items == null || items.isEmpty()) {
            throw new IllegalArgumentException("治理应用作业不能为空");
        }
        if (requestedBy == null || requestedBy.isBlank() || requestedAt == null || status == null || version < 0) {
            throw new IllegalArgumentException("治理应用作业信息不完整");
        }
        items = List.copyOf(items);
    }

    public enum Status {
        PENDING,
        RUNNING,
        FAILED,
        SUCCEEDED
    }
}
