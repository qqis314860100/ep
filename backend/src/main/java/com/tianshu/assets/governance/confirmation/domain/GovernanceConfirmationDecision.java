package com.tianshu.assets.governance.confirmation.domain;

import java.time.Instant;

public record GovernanceConfirmationDecision(
        long id,
        long roundId,
        long itemId,
        long resultVersionId,
        Decision decision,
        String comment,
        String confirmerUserId,
        Instant decidedAt,
        long version) {

    public GovernanceConfirmationDecision {
        if (roundId <= 0 || itemId <= 0 || resultVersionId <= 0) {
            throw new IllegalArgumentException("确认决定范围不能为空");
        }
        if (decision == null || confirmerUserId == null || confirmerUserId.isBlank() || decidedAt == null) {
            throw new IllegalArgumentException("确认决定、确认人和确认时间不能为空");
        }
        if (decision == Decision.REJECTED && (comment == null || comment.isBlank())) {
            throw new IllegalArgumentException("退回必须填写确认意见");
        }
        comment = comment == null ? "" : comment;
    }

    public enum Decision {
        APPROVED,
        REJECTED
    }
}
