package com.tianshu.assets.governance.acceptance.domain;

public record GovernanceOperationJobItem(
        long itemId,
        long resultVersionId,
        Status status,
        String errorReason) {

    public GovernanceOperationJobItem {
        if (itemId <= 0 || resultVersionId <= 0 || status == null) {
            throw new IllegalArgumentException("治理应用作业项不合法");
        }
        errorReason = errorReason == null ? "" : errorReason;
    }

    public GovernanceOperationJobItem succeeded() {
        return new GovernanceOperationJobItem(itemId, resultVersionId, Status.SUCCEEDED, "");
    }

    public GovernanceOperationJobItem failed(String reason) {
        return new GovernanceOperationJobItem(itemId, resultVersionId, Status.FAILED, reason);
    }

    public enum Status {
        PENDING,
        SUCCEEDED,
        FAILED
    }
}
