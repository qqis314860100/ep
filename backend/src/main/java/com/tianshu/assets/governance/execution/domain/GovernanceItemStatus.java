package com.tianshu.assets.governance.execution.domain;

public enum GovernanceItemStatus {
    PENDING,
    PROCESSING,
    SUBMITTED,
    CONFIRMED,
    ACCEPTED,
    BLOCKED,
    REWORK_REQUIRED;

    public boolean countsAsSubmitted() {
        return this == SUBMITTED || this == CONFIRMED || this == ACCEPTED;
    }

    public boolean countsAsConfirmed() {
        return this == CONFIRMED || this == ACCEPTED;
    }
}
