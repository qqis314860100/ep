package com.tianshu.assets.system.domain;

import java.time.Instant;

public record OperationLog(
        long id,
        String actorUserId,
        String action,
        String targetType,
        long targetId,
        String detailJson,
        Instant createdAt) {

    public OperationLog {
        actorUserId = actorUserId == null ? "" : actorUserId.trim();
        action = action == null ? "" : action.trim();
        targetType = targetType == null ? "" : targetType.trim();
        detailJson = detailJson == null ? "{}" : detailJson;
    }
}
