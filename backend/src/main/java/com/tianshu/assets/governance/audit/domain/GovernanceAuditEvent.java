package com.tianshu.assets.governance.audit.domain;

import java.time.Instant;

public record GovernanceAuditEvent(
        long id,
        long taskId,
        Long itemId,
        String aggregateType,
        long aggregateId,
        String action,
        int governanceRound,
        String actorUserId,
        String beforeJson,
        String afterJson,
        Instant createdAt) {

    public GovernanceAuditEvent {
        if (taskId <= 0 || aggregateType == null || aggregateType.isBlank()
                || action == null || action.isBlank() || governanceRound <= 0
                || actorUserId == null || actorUserId.isBlank() || createdAt == null) {
            throw new IllegalArgumentException("审计事件信息不完整");
        }
        beforeJson = beforeJson == null ? "{}" : beforeJson;
        afterJson = afterJson == null ? "{}" : afterJson;
    }
}
