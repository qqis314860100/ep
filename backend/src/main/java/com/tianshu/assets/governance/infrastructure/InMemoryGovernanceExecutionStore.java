package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionStore;
import com.tianshu.assets.governance.execution.domain.GovernanceItem;
import com.tianshu.assets.governance.execution.domain.GovernanceItemStatus;
import com.tianshu.assets.governance.execution.domain.GovernanceResultStatus;
import com.tianshu.assets.governance.execution.domain.GovernanceResultVersion;
import com.tianshu.assets.governance.task.application.GovernanceWorkflowStore;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryGovernanceExecutionStore implements GovernanceExecutionStore {

    private final GovernanceWorkflowStore workflowStore;
    private final Map<Long, GovernanceItem> changedItems = new LinkedHashMap<>();
    private final Map<Long, GovernanceResultVersion> results = new LinkedHashMap<>();
    private final AtomicLong nextResultId = new AtomicLong(1);

    public InMemoryGovernanceExecutionStore(GovernanceWorkflowStore workflowStore) {
        this.workflowStore = workflowStore;
    }

    @Override
    public synchronized GovernanceItem item(long itemId) {
        return changedItems.getOrDefault(itemId, workflowStore.item(itemId));
    }

    @Override
    public synchronized List<GovernanceItem> items(long taskId) {
        return workflowStore.items(taskId).stream()
                .map(item -> changedItems.getOrDefault(item.id(), item))
                .sorted(Comparator.comparingLong(GovernanceItem::id))
                .toList();
    }

    @Override
    public synchronized GovernanceResultVersion currentResult(long itemId) {
        var currentResultId = item(itemId).currentResultVersionId();
        return currentResultId == null ? null : results.get(currentResultId);
    }

    @Override
    public synchronized GovernanceResultVersion saveDraft(SaveDraft command) {
        var item = item(command.itemId());
        requireItemVersion(item, command.expectedItemVersion());
        if (item.assetVersion() != command.expectedAssetVersion()) {
            throw new GovernanceVersionConflictException("资产版本已变化，请刷新后重试");
        }
        var current = currentResult(item.id());
        if (current != null && current.status() != GovernanceResultStatus.DRAFT) {
            throw new GovernanceConflictException("已提交结果不可原地修改");
        }
        var saved = current == null
                ? new GovernanceResultVersion(
                        nextResultId.getAndIncrement(), item.id(), item.governanceRound(), 1,
                        command.field(), command.originalValueJson(), command.proposedValueJson(),
                        command.standardVersion(), command.dictionaryVersions(), GovernanceResultStatus.DRAFT,
                        command.actorUserId(), command.savedAt(), null, 0)
                : new GovernanceResultVersion(
                        current.id(), current.itemId(), current.governanceRound(), current.resultVersion(),
                        current.field(), current.originalValueJson(), command.proposedValueJson(),
                        current.standardVersion(), current.dictionaryVersions(), GovernanceResultStatus.DRAFT,
                        command.actorUserId(), command.savedAt(), null, current.version() + 1);
        results.put(saved.id(), saved);
        changedItems.put(item.id(), copyItem(
                item, GovernanceItemStatus.PROCESSING, item.version() + 1, saved.id()));
        return saved;
    }

    @Override
    public synchronized GovernanceResultVersion submit(Submit command) {
        var item = item(command.itemId());
        if (item.currentResultVersionId() == null
                || item.currentResultVersionId() != command.resultVersionId()) {
            throw new GovernanceVersionConflictException("治理结果已变化，请刷新后重试");
        }
        var current = results.get(command.resultVersionId());
        if (current == null || current.version() != command.expectedResultVersion()) {
            throw new GovernanceVersionConflictException("结果版本已变化，请刷新后重试");
        }
        if (current.status() != GovernanceResultStatus.DRAFT) {
            throw new GovernanceConflictException("治理结果已经提交");
        }
        var submitted = new GovernanceResultVersion(
                current.id(), current.itemId(), current.governanceRound(), current.resultVersion(),
                current.field(), current.originalValueJson(), current.proposedValueJson(),
                current.standardVersion(), current.dictionaryVersions(), GovernanceResultStatus.SUBMITTED,
                command.actorUserId(), current.savedAt(), command.submittedAt(), current.version() + 1);
        results.put(submitted.id(), submitted);
        changedItems.put(item.id(), copyItem(
                item, GovernanceItemStatus.SUBMITTED, item.version() + 1, submitted.id()));
        return submitted;
    }

    private void requireItemVersion(GovernanceItem item, long expectedVersion) {
        if (item.version() != expectedVersion) {
            throw new GovernanceVersionConflictException("治理项已变化，请刷新后重试");
        }
    }

    private GovernanceItem copyItem(
            GovernanceItem item,
            GovernanceItemStatus status,
            long version,
            Long currentResultVersionId) {
        return new GovernanceItem(
                item.id(), item.taskId(), item.planId(), item.issueId(), item.assetId(), item.targetField(),
                item.actionType(), item.responsibleUserId(), status, item.assetVersion(), item.governanceRound(),
                item.scopeFingerprint(), version, currentResultVersionId, item.blockReason(),
                item.reworkSourceItemId());
    }
}
