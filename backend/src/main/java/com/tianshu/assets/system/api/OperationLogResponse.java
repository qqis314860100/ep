package com.tianshu.assets.system.api;

import com.tianshu.assets.system.domain.OperationLog;
import java.time.Instant;

public record OperationLogResponse(
        long id,
        String actorUserId,
        String action,
        String targetType,
        long targetId,
        String detailJson,
        Instant createdAt) {

    public static OperationLogResponse from(OperationLog log) {
        return new OperationLogResponse(log.id(), log.actorUserId(), log.action(), log.targetType(), log.targetId(),
                log.detailJson(), log.createdAt());
    }
}
