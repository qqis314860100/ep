package com.tianshu.assets.governance.acceptance.application;

import com.tianshu.assets.governance.audit.application.GovernanceAuditService;
import com.tianshu.assets.governance.acceptance.domain.GovernanceOperationJob;
import com.tianshu.assets.governance.acceptance.domain.GovernanceOperationJobItem;
import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionStore;
import com.tianshu.assets.governance.execution.domain.GovernanceResultStatus;
import com.tianshu.assets.governance.issue.application.GovernanceIssueStore;
import com.tianshu.assets.governance.issue.domain.GovernanceIssueStatus;
import com.tianshu.assets.governance.task.application.GovernanceTaskStore;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GovernanceApplicationJobService {

    private final GovernanceAcceptanceStore acceptanceStore;
    private final GovernanceExecutionStore executionStore;
    private final GovernanceIssueStore issueStore;
    private final GovernanceTaskStore taskStore;
    private final GovernanceAssetPort assetPort;
    private final GovernanceAuditService auditService;

    @Autowired
    public GovernanceApplicationJobService(
            GovernanceAcceptanceStore acceptanceStore,
            GovernanceExecutionStore executionStore,
            GovernanceIssueStore issueStore,
            GovernanceTaskStore taskStore,
            GovernanceAssetPort assetPort,
            GovernanceAuditService auditService) {
        this.acceptanceStore = acceptanceStore;
        this.executionStore = executionStore;
        this.issueStore = issueStore;
        this.taskStore = taskStore;
        this.assetPort = assetPort;
        this.auditService = auditService;
    }

    public GovernanceApplicationJobService(
            GovernanceAcceptanceStore acceptanceStore,
            GovernanceExecutionStore executionStore,
            GovernanceIssueStore issueStore,
            GovernanceTaskStore taskStore,
            GovernanceAssetPort assetPort) {
        this(acceptanceStore, executionStore, issueStore, taskStore, assetPort, null);
    }

    public JobSummary run(long jobId) {
        var current = requireJob(jobId);
        if (current.status() == GovernanceOperationJob.Status.SUCCEEDED) return summarize(current);
        var job = acceptanceStore.claimApplicationJob(jobId, current.version());
        var targetIds = job.items().stream()
                .filter(item -> item.status() != GovernanceOperationJobItem.Status.SUCCEEDED)
                .map(GovernanceOperationJobItem::itemId)
                .toList();

        for (var itemId : targetIds) {
            var jobItem = item(job, itemId);
            GovernanceOperationJobItem updated;
            try {
                apply(job, jobItem);
                updated = jobItem.succeeded();
            } catch (RuntimeException exception) {
                updated = jobItem.failed(errorMessage(exception));
                if (auditService != null) {
                    var failedItem = executionStore.item(itemId);
                    auditService.record(job.taskId(), itemId, "RESULT", jobItem.resultVersionId(),
                            "APPLICATION_FAILED", failedItem.governanceRound(), job.requestedBy(), "{}",
                            "{\"errorCode\":\"" + errorMessage(exception) + "\"}");
                }
            }
            job = replaceItem(job, updated, GovernanceOperationJob.Status.RUNNING);
            job = acceptanceStore.updateApplicationJob(job, job.version());
        }

        var allItemsSucceeded = job.items().stream().allMatch(
                item -> item.status() == GovernanceOperationJobItem.Status.SUCCEEDED);
        if (allItemsSucceeded) {
            try {
                completeTask(job);
            } catch (RuntimeException exception) {
                var lastItem = job.items().getLast().failed(errorMessage(exception));
                job = acceptanceStore.updateApplicationJob(
                        replaceItem(job, lastItem, GovernanceOperationJob.Status.RUNNING), job.version());
                allItemsSucceeded = false;
            }
        }
        var finalStatus = allItemsSucceeded
                ? GovernanceOperationJob.Status.SUCCEEDED : GovernanceOperationJob.Status.FAILED;
        job = acceptanceStore.updateApplicationJob(
                copyJob(job, job.items(), finalStatus), job.version());
        return summarize(job);
    }

    public JobSummary retry(long jobId) {
        var job = requireJob(jobId);
        if (job.status() != GovernanceOperationJob.Status.FAILED) {
            throw new GovernanceConflictException("治理应用作业当前不可重试");
        }
        return run(jobId);
    }

    public JobSummary get(long jobId) {
        return summarize(requireJob(jobId));
    }

    private void apply(GovernanceOperationJob job, GovernanceOperationJobItem jobItem) {
        var item = executionStore.item(jobItem.itemId());
        if (item.taskId() != job.taskId() || item.currentResultVersionId() == null
                || item.currentResultVersionId() != jobItem.resultVersionId()) {
            throw new GovernanceConflictException("治理项结果版本与应用作业不一致");
        }
        var result = executionStore.currentResult(item.id());
        if (result == null || result.id() != jobItem.resultVersionId()
                || result.status() != GovernanceResultStatus.SUBMITTED
                        && result.status() != GovernanceResultStatus.APPLIED) {
            throw new GovernanceConflictException("治理结果已变化，无法正式应用");
        }
        var appliedForAsset = job.items().stream()
                .filter(candidate -> candidate.status() == GovernanceOperationJobItem.Status.SUCCEEDED)
                .map(candidate -> executionStore.item(candidate.itemId()))
                .filter(candidate -> candidate.assetId() == item.assetId())
                .count();
        var expectedCurrentAssetVersion = item.assetVersion() + appliedForAsset;
        // 版本乐观锁由 assetPort.applyFieldResult 内部执行（内存适配器与 JDBC 适配器各自校验），
        // 此处不再重复前置校验，保证扫描产生问题的资产版本（updatedAt 时间戳）也能对齐。
        assetPort.applyFieldResult(
                item.id(), item.assetId(), result.field(), result.proposedValueJson(),
                expectedCurrentAssetVersion, job.requestedBy());
        executionStore.markApplied(result.id(), result.version());
        if (auditService != null) {
            auditService.record(item.taskId(), item.id(), "RESULT", result.id(), "APPLICATION_SUCCEEDED",
                    item.governanceRound(), job.requestedBy(),
                    "{\"status\":\"SUBMITTED\",\"version\":" + result.version() + "}",
                    "{\"status\":\"APPLIED\"}");
        }
        var issue = issueStore.findByIds(List.of(item.issueId())).stream().findFirst()
                .orElseThrow(() -> new GovernanceConflictException("治理项关联问题不存在"));
        issueStore.resolve(issue.id(), issue.version());
    }

    private void completeTask(GovernanceOperationJob job) {
        var assetIds = job.items().stream()
                .map(item -> executionStore.item(item.itemId()).assetId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (var assetId : assetIds) {
            var hasBlockingIssue = issueStore.find(null, GovernanceIssueStatus.OPEN, assetId).stream()
                    .anyMatch(issue -> issue.blocking());
            if (!hasBlockingIssue && assetPort.meetsAllActiveStandards(assetId)) {
                var snapshot = assetPort.snapshot(assetId);
                assetPort.markStandardized(assetId, snapshot.version(), job.requestedBy());
            }
        }
        synchronized (taskStore) {
            var task = taskStore.findById(job.taskId())
                    .orElseThrow(() -> new IllegalArgumentException("治理任务不存在"));
            if (task.status() == GovernanceTaskStatus.COMPLETED) return;
            if (task.status() != GovernanceTaskStatus.PENDING_ACCEPTANCE) {
                throw new GovernanceConflictException("治理任务不在待正式应用状态");
            }
            taskStore.update(copyTask(task, GovernanceTaskStatus.COMPLETED), task.version());
            if (auditService != null) {
                auditService.record(job.taskId(), null, "TASK", job.taskId(), "TASK_COMPLETED",
                        task.currentRound(), job.requestedBy(),
                        "{\"status\":\"PENDING_ACCEPTANCE\"}", "{\"status\":\"COMPLETED\"}");
            }
        }
    }

    private GovernanceOperationJob requireJob(long jobId) {
        return acceptanceStore.applicationJob(jobId)
                .orElseThrow(() -> new IllegalArgumentException("治理应用作业不存在"));
    }

    private GovernanceOperationJobItem item(GovernanceOperationJob job, long itemId) {
        return job.items().stream().filter(item -> item.itemId() == itemId).findFirst().orElseThrow();
    }

    private GovernanceOperationJob replaceItem(
            GovernanceOperationJob job,
            GovernanceOperationJobItem replacement,
            GovernanceOperationJob.Status status) {
        var items = job.items().stream()
                .map(item -> item.itemId() == replacement.itemId() ? replacement : item)
                .toList();
        return copyJob(job, items, status);
    }

    private GovernanceOperationJob copyJob(
            GovernanceOperationJob job,
            List<GovernanceOperationJobItem> items,
            GovernanceOperationJob.Status status) {
        return new GovernanceOperationJob(
                job.id(), job.taskId(), job.acceptanceRoundId(), items, job.requestedBy(),
                job.requestedAt(), status, job.version());
    }

    private GovernanceTask copyTask(GovernanceTask task, GovernanceTaskStatus status) {
        return new GovernanceTask(
                task.id(), task.taskNumber(), task.name(), task.actionType(), task.issueType(),
                task.ownerUserId(), task.ownerName(), task.assigneeId(), task.dueDate(), status,
                task.currentRound(), task.workflowVersion(), task.scopeSnapshotId(), task.qualityPolicySnapshotId(),
                task.legacyTotal(), task.legacyCompleted(), task.version());
    }

    private JobSummary summarize(GovernanceOperationJob job) {
        var succeeded = (int) job.items().stream()
                .filter(item -> item.status() == GovernanceOperationJobItem.Status.SUCCEEDED).count();
        var failed = (int) job.items().stream()
                .filter(item -> item.status() == GovernanceOperationJobItem.Status.FAILED).count();
        var processing = job.status() == GovernanceOperationJob.Status.RUNNING
                ? job.items().size() - succeeded - failed : 0;
        var errors = new LinkedHashMap<Long, String>();
        job.items().stream().filter(item -> item.status() == GovernanceOperationJobItem.Status.FAILED)
                .forEach(item -> errors.put(item.itemId(), item.errorReason()));
        return new JobSummary(
                job.id(), job.taskId(), job.items().size(), succeeded, failed, processing,
                Map.copyOf(errors), job.status() == GovernanceOperationJob.Status.FAILED);
    }

    private String errorMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    public record JobSummary(
            long jobId,
            long taskId,
            int total,
            int succeeded,
            int failed,
            int processing,
            Map<Long, String> errors,
            boolean retryable) {
        public JobSummary {
            errors = Map.copyOf(errors);
        }
    }
}
