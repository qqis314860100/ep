package com.tianshu.assets.governance.scan.application;

import com.tianshu.assets.governance.scan.domain.GovernanceScanRun;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GovernanceScanRunStore {
    List<GovernanceScanRun> findAll();
    Optional<GovernanceScanRun> findById(long id);
    GovernanceScanRun start(GovernanceScanRun run);
    GovernanceScanRun succeed(long id, long expectedVersion, Counts counts, Instant finishedAt);
    GovernanceScanRun fail(long id, long expectedVersion, Counts counts, String errorMessage, Instant finishedAt);

    record Counts(long scannedAssetCount, long createdIssueCount, long reopenedIssueCount, long unchangedIssueCount) {}
}
