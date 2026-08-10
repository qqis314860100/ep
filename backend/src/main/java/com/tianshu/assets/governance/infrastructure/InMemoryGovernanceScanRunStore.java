package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.scan.application.GovernanceScanRunStore;
import com.tianshu.assets.governance.scan.domain.GovernanceScanRun;
import com.tianshu.assets.governance.scan.domain.GovernanceScanRunStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryGovernanceScanRunStore implements GovernanceScanRunStore {
    private final Map<Long, GovernanceScanRun> runs = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override public List<GovernanceScanRun> findAll() {
        return runs.values().stream().sorted(Comparator.comparingLong(GovernanceScanRun::id).reversed()).toList();
    }
    @Override public Optional<GovernanceScanRun> findById(long id) { return Optional.ofNullable(runs.get(id)); }
    @Override public synchronized GovernanceScanRun start(GovernanceScanRun run) {
        var created = copy(run, nextId.getAndIncrement(), run.version());
        runs.put(created.id(), created); return created;
    }
    @Override public synchronized GovernanceScanRun succeed(long id, long expectedVersion, Counts counts, Instant finishedAt) {
        var current = require(id, expectedVersion);
        var updated = new GovernanceScanRun(current.id(), current.triggerType(), GovernanceScanRunStatus.SUCCEEDED,
                current.startedAt(), finishedAt, counts.scannedAssetCount(), counts.createdIssueCount(), counts.reopenedIssueCount(), counts.unchangedIssueCount(), "", current.retryOfRunId(), current.version() + 1);
        runs.put(id, updated); return updated;
    }
    @Override public synchronized GovernanceScanRun fail(long id, long expectedVersion, Counts counts, String errorMessage, Instant finishedAt) {
        var current = require(id, expectedVersion);
        var updated = new GovernanceScanRun(current.id(), current.triggerType(), GovernanceScanRunStatus.FAILED,
                current.startedAt(), finishedAt, counts.scannedAssetCount(), counts.createdIssueCount(), counts.reopenedIssueCount(), counts.unchangedIssueCount(), errorMessage, current.retryOfRunId(), current.version() + 1);
        runs.put(id, updated); return updated;
    }
    private GovernanceScanRun require(long id, long version) {
        var current = findById(id).orElseThrow(() -> new GovernanceConflictException("扫描运行不存在"));
        if (current.version() != version) throw new GovernanceConflictException("扫描运行已被其他用户更新，请刷新后重试");
        return current;
    }
    private GovernanceScanRun copy(GovernanceScanRun source, long id, long version) {
        return new GovernanceScanRun(id, source.triggerType(), source.status(), source.startedAt(), source.finishedAt(), source.scannedAssetCount(), source.createdIssueCount(), source.reopenedIssueCount(), source.unchangedIssueCount(), source.errorMessage(), source.retryOfRunId(), version);
    }
}
