package com.tianshu.assets.governance.task.domain;

public enum GovernanceTaskStatus {
    DRAFT,
    IN_PROGRESS,
    PENDING_CONFIRMATION,
    PENDING_ACCEPTANCE,
    REWORK_REQUIRED,
    COMPLETED;

    public GovernanceTaskStatus moveTo(GovernanceTaskStatus target) {
        if (!canMoveTo(target)) {
            throw new IllegalStateException("治理任务状态不能从 " + this + " 跳转到 " + target);
        }
        return target;
    }

    private boolean canMoveTo(GovernanceTaskStatus target) {
        return switch (this) {
            case DRAFT -> target == IN_PROGRESS;
            case IN_PROGRESS -> target == PENDING_CONFIRMATION;
            case PENDING_CONFIRMATION -> target == PENDING_ACCEPTANCE || target == REWORK_REQUIRED;
            case PENDING_ACCEPTANCE -> target == COMPLETED || target == REWORK_REQUIRED;
            case REWORK_REQUIRED -> target == IN_PROGRESS;
            case COMPLETED -> false;
        };
    }
}
