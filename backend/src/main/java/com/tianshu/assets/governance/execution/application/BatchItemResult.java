package com.tianshu.assets.governance.execution.application;

public record BatchItemResult(
        long itemId,
        BatchOutcome outcome,
        Long resultVersionId,
        String errorCode,
        String message,
        Long currentVersion) {

    public enum BatchOutcome {
        SUCCESS,
        VALIDATION_FAILED,
        CONFLICT
    }
}
