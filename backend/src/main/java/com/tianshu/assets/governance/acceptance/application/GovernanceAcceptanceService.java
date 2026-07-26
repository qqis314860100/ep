package com.tianshu.assets.governance.acceptance.application;

import com.tianshu.assets.governance.audit.application.GovernanceAuditService;
import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceRound;
import com.tianshu.assets.governance.acceptance.domain.GovernanceQualityMetric;
import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.application.GovernanceTaskStateException;
import com.tianshu.assets.governance.application.GovernanceValidationException;
import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionStore;
import com.tianshu.assets.governance.execution.domain.GovernanceItem;
import com.tianshu.assets.governance.execution.domain.GovernanceItemStatus;
import com.tianshu.assets.governance.execution.domain.GovernanceResultStatus;
import com.tianshu.assets.governance.task.application.GovernanceTaskStore;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GovernanceAcceptanceService {

    private final GovernanceAcceptanceStore acceptanceStore;
    private final GovernanceExecutionStore executionStore;
    private final GovernanceTaskStore taskStore;
    private final GovernanceJobDispatcher jobDispatcher;
    private final Clock clock;
    private final GovernanceAuditService auditService;

    @Autowired
    public GovernanceAcceptanceService(
            GovernanceAcceptanceStore acceptanceStore,
            GovernanceExecutionStore executionStore,
            GovernanceTaskStore taskStore,
            GovernanceJobDispatcher jobDispatcher,
            GovernanceAuditService auditService) {
        this(acceptanceStore, executionStore, taskStore, jobDispatcher, Clock.systemUTC(), auditService);
    }

    public GovernanceAcceptanceService(
            GovernanceAcceptanceStore acceptanceStore,
            GovernanceExecutionStore executionStore,
            GovernanceTaskStore taskStore,
            GovernanceJobDispatcher jobDispatcher) {
        this(acceptanceStore, executionStore, taskStore, jobDispatcher, Clock.systemUTC(), null);
    }

    public GovernanceAcceptanceService(
            GovernanceAcceptanceStore acceptanceStore,
            GovernanceExecutionStore executionStore,
            GovernanceTaskStore taskStore,
            Clock clock) {
        this(acceptanceStore, executionStore, taskStore, GovernanceJobDispatcher.noOp(), clock);
    }

    public GovernanceAcceptanceService(
            GovernanceAcceptanceStore acceptanceStore,
            GovernanceExecutionStore executionStore,
            GovernanceTaskStore taskStore,
            Clock clock,
            GovernanceAuditService auditService) {
        this(acceptanceStore, executionStore, taskStore, GovernanceJobDispatcher.noOp(), clock, auditService);
    }

    public GovernanceAcceptanceService(
            GovernanceAcceptanceStore acceptanceStore,
            GovernanceExecutionStore executionStore,
            GovernanceTaskStore taskStore,
            GovernanceJobDispatcher jobDispatcher,
            Clock clock) {
        this(acceptanceStore, executionStore, taskStore, jobDispatcher, clock, null);
    }

    private GovernanceAcceptanceService(
            GovernanceAcceptanceStore acceptanceStore,
            GovernanceExecutionStore executionStore,
            GovernanceTaskStore taskStore,
            GovernanceJobDispatcher jobDispatcher,
            Clock clock,
            GovernanceAuditService auditService) {
        this.acceptanceStore = acceptanceStore;
        this.executionStore = executionStore;
        this.taskStore = taskStore;
        this.jobDispatcher = jobDispatcher;
        this.clock = clock;
        this.auditService = auditService;
    }

    public GovernanceAcceptanceRound current(long taskId) {
        return acceptanceStore.currentRound(taskId)
                .orElseThrow(() -> new GovernanceConflictException("治理任务尚未创建验收轮次"));
    }

    @Transactional
    public CompletionResult complete(
            long taskId,
            long roundId,
            long expectedRoundVersion,
            String operatorUserId) {
        if (operatorUserId == null || operatorUserId.isBlank()) {
            throw new GovernanceValidationException("验收操作人不能为空");
        }
        synchronized (taskStore) {
            var round = acceptanceStore.round(roundId);
            if (round.taskId() != taskId) {
                throw new GovernanceValidationException("验收轮次不属于当前治理任务");
            }
            if (round.status() != GovernanceAcceptanceRound.Status.OPEN) {
                throw new GovernanceConflictException("验收轮次已经完成");
            }
            if (round.version() != expectedRoundVersion) {
                throw new GovernanceVersionConflictException("验收轮次已变化，请刷新后重试");
            }
            var task = taskStore.findById(taskId)
                    .orElseThrow(() -> new IllegalArgumentException("治理任务不存在"));
            if (task.status() != GovernanceTaskStatus.PENDING_ACCEPTANCE) {
                throw new GovernanceTaskStateException("治理任务不在待验收状态");
            }
            if (task.currentRound() != round.governanceRound()) {
                throw new GovernanceConflictException("验收轮次与任务当前轮次不一致");
            }
            var items = executionStore.items(taskId);
            if (items.isEmpty() || items.stream().anyMatch(item -> item.status() != GovernanceItemStatus.CONFIRMED)) {
                throw new GovernanceValidationException("验收前全部治理项必须完成业务确认");
            }
            validateCoverage(round, items);

            var affectedItemIds = affectedItemIds(round, items);
            if (!affectedItemIds.isEmpty()) {
                var statuses = new LinkedHashMap<Long, GovernanceItemStatus>();
                items.forEach(item -> statuses.put(
                        item.id(), affectedItemIds.contains(item.id())
                                ? GovernanceItemStatus.REWORK_REQUIRED : GovernanceItemStatus.CONFIRMED));
                executionStore.updateItemStatuses(statuses);
                var updatedTask = taskStore.update(
                        copyTask(task, task.status().moveTo(GovernanceTaskStatus.REWORK_REQUIRED),
                                task.currentRound()),
                        task.version());
                var completedRound = completeRound(
                        round, GovernanceAcceptanceRound.Status.FAILED, expectedRoundVersion);
                var completion = new CompletionResult(
                        taskId, roundId, completedRound.status(), updatedTask.status(),
                        List.copyOf(affectedItemIds), null);
                if (auditService != null) {
                    auditService.record(taskId, null, "ACCEPTANCE_ROUND", roundId, "ACCEPTANCE_COMPLETED",
                            round.governanceRound(), operatorUserId,
                            "{\"status\":\"OPEN\",\"version\":" + expectedRoundVersion + "}",
                            "{\"status\":\"FAILED\",\"affectedItems\":" + affectedItemIds.size() + "}");
                }
                return completion;
            }

            var resultVersionIds = new LinkedHashMap<Long, Long>();
            items.forEach(item -> {
                var result = executionStore.currentResult(item.id());
                if (result == null || result.status() != GovernanceResultStatus.SUBMITTED) {
                    throw new GovernanceConflictException("已验收治理项缺少待应用结果");
                }
                resultVersionIds.put(item.id(), result.id());
            });
            var statuses = new LinkedHashMap<Long, GovernanceItemStatus>();
            items.forEach(item -> statuses.put(item.id(), GovernanceItemStatus.ACCEPTED));
            executionStore.updateItemStatuses(statuses);
            var completedRound = completeRound(
                    round, GovernanceAcceptanceRound.Status.PASSED, expectedRoundVersion);
            var job = acceptanceStore.createApplicationJob(
                    taskId, roundId, resultVersionIds, operatorUserId, Instant.now(clock));
            jobDispatcher.dispatch(job.id());
            var completion = new CompletionResult(
                    taskId, roundId, completedRound.status(), task.status(), List.of(), job.id());
            if (auditService != null) {
                auditService.record(taskId, null, "ACCEPTANCE_ROUND", roundId, "ACCEPTANCE_COMPLETED",
                        round.governanceRound(), operatorUserId,
                        "{\"status\":\"OPEN\",\"version\":" + expectedRoundVersion + "}",
                        "{\"status\":\"PASSED\",\"applicationJobId\":" + job.id() + "}");
            }
            return completion;
        }
    }

    private void validateCoverage(GovernanceAcceptanceRound round, List<GovernanceItem> items) {
        if (round.policy().samplingRequired()
                && (round.samples().size() != Math.min(round.policy().sampleSize(), items.size())
                        || round.samples().stream().anyMatch(sample -> sample.passed() == null))) {
            throw new GovernanceValidationException("固定验收样本尚未全部检查");
        }
        var universalMetrics = java.util.Set.of(
                GovernanceQualityMetric.REQUIRED_FIELD_COMPLETENESS,
                GovernanceQualityMetric.ASSET_SCOPE_VALIDITY,
                GovernanceQualityMetric.OWNER_COVERAGE);
        if (round.metricResults().stream()
                .filter(result -> universalMetrics.contains(result.metric()))
                .anyMatch(result -> result.denominator() != items.size())) {
            throw new GovernanceValidationException("质量指标尚未覆盖全部确认项");
        }
        var itemIds = items.stream().map(GovernanceItem::id).collect(java.util.stream.Collectors.toSet());
        if (round.metricResults().stream().anyMatch(result ->
                !result.passed() && (result.affectedItemIds().isEmpty()
                        || !itemIds.containsAll(result.affectedItemIds())))) {
            throw new GovernanceValidationException("失败质量指标无法解析受影响治理项");
        }
        if (round.samples().stream().anyMatch(sample ->
                Boolean.FALSE.equals(sample.passed()) && sample.issueDescription().isBlank())) {
            throw new GovernanceValidationException("退回样本必须填写问题说明");
        }
    }

    private LinkedHashSet<Long> affectedItemIds(
            GovernanceAcceptanceRound round, List<GovernanceItem> items) {
        var itemIds = items.stream().map(GovernanceItem::id).collect(java.util.stream.Collectors.toSet());
        var affected = new LinkedHashSet<Long>();
        round.metricResults().stream().filter(result -> !result.passed())
                .forEach(result -> affected.addAll(result.affectedItemIds()));
        round.samples().stream().filter(sample -> Boolean.FALSE.equals(sample.passed()))
                .map(sample -> sample.itemId()).forEach(affected::add);
        if (!itemIds.containsAll(affected)) {
            throw new GovernanceValidationException("验收退回项不属于当前治理任务");
        }
        return affected;
    }

    private GovernanceAcceptanceRound completeRound(
            GovernanceAcceptanceRound round,
            GovernanceAcceptanceRound.Status status,
            long expectedVersion) {
        return acceptanceStore.updateRound(new GovernanceAcceptanceRound(
                round.id(), round.taskId(), round.governanceRound(), round.policy(),
                round.metricResults(), round.samples(), status, round.createdAt(), Instant.now(clock),
                round.version()), expectedVersion);
    }

    private GovernanceTask copyTask(
            GovernanceTask task, GovernanceTaskStatus status, int currentRound) {
        return new GovernanceTask(
                task.id(), task.taskNumber(), task.name(), task.actionType(), task.issueType(),
                task.ownerUserId(), task.ownerName(), task.assigneeId(), task.dueDate(), status,
                currentRound, task.workflowVersion(), task.scopeSnapshotId(), task.qualityPolicySnapshotId(),
                task.legacyTotal(), task.legacyCompleted(), task.version());
    }

    public record CompletionResult(
            long taskId,
            long roundId,
            GovernanceAcceptanceRound.Status roundStatus,
            GovernanceTaskStatus taskStatus,
            List<Long> affectedItemIds,
            Long applicationJobId) {
        public CompletionResult {
            affectedItemIds = List.copyOf(affectedItemIds);
        }
    }
}
