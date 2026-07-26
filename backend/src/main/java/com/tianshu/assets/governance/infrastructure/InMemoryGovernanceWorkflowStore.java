package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.governance.execution.domain.GovernanceItem;
import com.tianshu.assets.governance.task.application.GovernanceWorkflowStore;
import com.tianshu.assets.governance.task.domain.GovernanceScopeItem;
import com.tianshu.assets.governance.task.domain.GovernanceScopeSnapshot;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryGovernanceWorkflowStore implements GovernanceWorkflowStore {

    private final Map<Long, GovernanceScopeSnapshot> snapshots = new LinkedHashMap<>();
    private final Map<Long, List<GovernanceScopeItem>> scopeItems = new LinkedHashMap<>();
    private final Map<Long, List<GovernanceItem>> itemsByTask = new LinkedHashMap<>();
    private final AtomicLong nextSnapshotId = new AtomicLong(1);
    private final AtomicLong nextRuleSnapshotId = new AtomicLong(1);
    private final AtomicLong nextItemId = new AtomicLong(1);

    @Override
    public synchronized FrozenWorkflow freeze(FreezeCommand command) {
        if (itemsByTask.containsKey(command.taskId())) {
            throw new IllegalStateException("治理任务范围已经固化");
        }
        var snapshotId = nextSnapshotId.getAndIncrement();
        var ruleSnapshotId = nextRuleSnapshotId.getAndIncrement();
        var ruleSnapshot = command.ruleSnapshot().withId(ruleSnapshotId);
        var frozenScopeItems = command.scopeItems().stream()
                .map(item -> new GovernanceScopeItem(
                        snapshotId, item.taskId(), item.planId(), item.issueId(), item.assetId(),
                        item.targetField(), item.targetPath(), item.originalFactJson(), item.assetVersion(),
                        item.ruleVersion(), item.scopeFingerprint(), item.responsibleUserId()))
                .toList();
        var frozenItems = command.items().stream()
                .map(item -> new GovernanceItem(
                        nextItemId.getAndIncrement(), item.taskId(), item.planId(), item.issueId(), item.assetId(),
                        item.targetField(), item.actionType(), item.responsibleUserId(), item.status(),
                        item.assetVersion(), item.governanceRound(), item.scopeFingerprint(), item.version(),
                        item.currentResultVersionId(), item.blockReason(), item.reworkSourceItemId()))
                .toList();
        var snapshot = new GovernanceScopeSnapshot(
                snapshotId, command.taskId(), command.claimedIssueIds(), command.assetIds(), ruleSnapshot,
                command.createdBy(), command.frozenAt(), frozenScopeItems.size());
        snapshots.put(snapshotId, snapshot);
        scopeItems.put(snapshotId, List.copyOf(frozenScopeItems));
        itemsByTask.put(command.taskId(), List.copyOf(frozenItems));
        return new FrozenWorkflow(snapshotId, ruleSnapshotId);
    }

    @Override
    public synchronized void discard(long scopeSnapshotId) {
        var snapshot = snapshots.remove(scopeSnapshotId);
        scopeItems.remove(scopeSnapshotId);
        if (snapshot != null) itemsByTask.remove(snapshot.taskId());
    }

    @Override
    public synchronized GovernanceScopeSnapshot scopeSnapshot(long scopeSnapshotId) {
        var snapshot = snapshots.get(scopeSnapshotId);
        if (snapshot == null) throw new IllegalArgumentException("治理范围快照不存在");
        return new GovernanceScopeSnapshot(
                snapshot.id(), snapshot.taskId(), snapshot.claimedIssueIds(), snapshot.assetIds(),
                snapshot.ruleSnapshot(), snapshot.createdBy(), snapshot.frozenAt(), snapshot.itemCount());
    }

    @Override
    public synchronized List<GovernanceScopeItem> scopeItems(long scopeSnapshotId) {
        return List.copyOf(scopeItems.getOrDefault(scopeSnapshotId, List.of()));
    }

    @Override
    public synchronized List<GovernanceItem> items(long taskId) {
        return itemsByTask.getOrDefault(taskId, List.of()).stream()
                .sorted(Comparator.comparingLong(GovernanceItem::id))
                .toList();
    }
}
