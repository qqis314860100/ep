package com.tianshu.assets.governance.scan.domain;

import java.time.Instant;

public record GovernanceScanRun(
        long id,
        GovernanceScanTriggerType triggerType,
        GovernanceScanRunStatus status,
        Instant startedAt,
        Instant finishedAt,
        long scannedAssetCount,
        long createdIssueCount,
        long reopenedIssueCount,
        long unchangedIssueCount,
        String errorMessage,
        Long retryOfRunId,
        long version) {

    public GovernanceScanRun {
        if (triggerType == null || status == null) throw new IllegalArgumentException("扫描运行状态不能为空");
        if (startedAt == null) throw new IllegalArgumentException("扫描开始时间不能为空");
        if (scannedAssetCount < 0 || createdIssueCount < 0 || reopenedIssueCount < 0 || unchangedIssueCount < 0 || version < 0) {
            throw new IllegalArgumentException("扫描运行计数不合法");
        }
        errorMessage = errorMessage == null ? "" : errorMessage;
    }
}
