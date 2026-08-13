package com.tianshu.assets.system.domain;

import java.time.Instant;

public record OperationLogCriteria(
        String actorUserId,
        String action,
        String targetType,
        Instant from,
        Instant to,
        int page,
        int perPage) {

    public OperationLogCriteria {
        actorUserId = actorUserId == null ? "" : actorUserId.trim();
        action = action == null ? "" : action.trim();
        targetType = targetType == null ? "" : targetType.trim();
        page = Math.max(page, 1);
        perPage = Math.min(Math.max(perPage, 1), 100);
    }
}
