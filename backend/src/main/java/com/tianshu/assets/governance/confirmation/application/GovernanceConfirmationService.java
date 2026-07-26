package com.tianshu.assets.governance.confirmation.application;

import com.tianshu.assets.governance.audit.application.GovernanceAuditService;
import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.application.GovernanceTaskStateException;
import com.tianshu.assets.governance.application.GovernanceValidationException;
import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import com.tianshu.assets.governance.confirmation.domain.GovernanceConfirmationDecision;
import com.tianshu.assets.governance.confirmation.domain.GovernanceConfirmationDecision.Decision;
import com.tianshu.assets.governance.confirmation.domain.GovernanceConfirmationRound;
import com.tianshu.assets.governance.confirmation.domain.GovernanceConfirmationRound.Status;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GovernanceConfirmationService {

    private final GovernanceConfirmationStore confirmationStore;
    private final GovernanceExecutionStore executionStore;
    private final GovernanceTaskStore taskStore;
    private final AssetResponsibilityPort responsibilityPort;
    private final Clock clock;
    private final GovernanceAuditService auditService;

    @Autowired
    public GovernanceConfirmationService(
            GovernanceConfirmationStore confirmationStore,
            GovernanceExecutionStore executionStore,
            GovernanceTaskStore taskStore,
            AssetResponsibilityPort responsibilityPort,
            GovernanceAuditService auditService) {
        this(confirmationStore, executionStore, taskStore, responsibilityPort, Clock.systemUTC(), auditService);
    }

    public GovernanceConfirmationService(
            GovernanceConfirmationStore confirmationStore,
            GovernanceExecutionStore executionStore,
            GovernanceTaskStore taskStore,
            AssetResponsibilityPort responsibilityPort) {
        this(confirmationStore, executionStore, taskStore, responsibilityPort, Clock.systemUTC(), null);
    }

    public GovernanceConfirmationService(
            GovernanceConfirmationStore confirmationStore,
            GovernanceExecutionStore executionStore,
            GovernanceTaskStore taskStore,
            AssetResponsibilityPort responsibilityPort,
            Clock clock) {
        this(confirmationStore, executionStore, taskStore, responsibilityPort, clock, null);
    }

    public GovernanceConfirmationService(
            GovernanceConfirmationStore confirmationStore,
            GovernanceExecutionStore executionStore,
            GovernanceTaskStore taskStore,
            AssetResponsibilityPort responsibilityPort,
            Clock clock,
            GovernanceAuditService auditService) {
        this.confirmationStore = confirmationStore;
        this.executionStore = executionStore;
        this.taskStore = taskStore;
        this.responsibilityPort = responsibilityPort;
        this.clock = clock;
        this.auditService = auditService;
    }

    public ConfirmationView current(long taskId) {
        var round = confirmationStore.currentRound(taskId)
                .orElseThrow(() -> new GovernanceConflictException("治理任务尚未创建确认轮次"));
        return view(round);
    }

    @Transactional
    public ConfirmationView decide(long roundId, long itemId, DecisionCommand command) {
        if (command == null || command.decision() == null || command.confirmerUserId() == null
                || command.confirmerUserId().isBlank()) {
            throw new GovernanceValidationException("确认决定和确认人不能为空");
        }
        if (command.decisionVersion() != 0) {
            throw new GovernanceVersionConflictException("确认决定已变化，请刷新后重试");
        }
        var round = requirePending(roundId);
        var item = requireRoundItem(round, itemId);
        var result = requireSubmittedRoundResult(round, item);
        requireResponsible(item, command.confirmerUserId());
        confirmationStore.insertDecision(new GovernanceConfirmationDecision(
                0, round.id(), item.id(), result.id(), command.decision(), command.comment(),
                command.confirmerUserId(), Instant.now(clock), 0));
        return view(round);
    }

    @Transactional
    public ConfirmationView batchApprove(long roundId, List<Long> itemIds, String confirmerUserId) {
        var round = requirePending(roundId);
        if (itemIds == null || itemIds.isEmpty() || itemIds.stream().distinct().count() != itemIds.size()) {
            throw new GovernanceValidationException("批量通过治理项不能为空且不能重复");
        }
        var items = itemIds.stream().map(itemId -> requireRoundItem(round, itemId)).toList();
        var contexts = items.stream().map(item -> {
            var result = requireSubmittedRoundResult(round, item);
            var responsibility = requireResponsible(item, confirmerUserId);
            return new BatchContext(item, result.id(), result.field().name(), responsibility.responsibilityScope());
        }).toList();
        var resultType = contexts.getFirst().resultType();
        var scope = contexts.getFirst().responsibilityScope();
        if (contexts.stream().anyMatch(context -> !resultType.equals(context.resultType())
                || !scope.equals(context.responsibilityScope()))) {
            throw new GovernanceValidationException("批量通过仅支持同一结果类型和责任范围");
        }
        confirmationStore.insertDecisions(contexts.stream().map(context ->
                new GovernanceConfirmationDecision(
                        0, round.id(), context.item().id(), context.resultVersionId(), Decision.APPROVED, "",
                        confirmerUserId, Instant.now(clock), 0)).toList());
        return view(round);
    }

    @Transactional
    public CompletionResult complete(long taskId, long roundId, long expectedRoundVersion) {
        synchronized (taskStore) {
            var round = requirePending(roundId);
            if (round.taskId() != taskId) throw new GovernanceValidationException("确认轮次不属于当前治理任务");
            if (round.version() != expectedRoundVersion) {
                throw new GovernanceVersionConflictException("确认轮次已变化，请刷新后重试");
            }
            var task = taskStore.findById(taskId)
                    .orElseThrow(() -> new IllegalArgumentException("治理任务不存在"));
            if (task.status() != GovernanceTaskStatus.PENDING_CONFIRMATION) {
                throw new GovernanceTaskStateException("治理任务不在待确认状态");
            }
            var decisions = confirmationStore.decisions(roundId);
            var byItem = new LinkedHashMap<Long, GovernanceConfirmationDecision>();
            decisions.forEach(decision -> byItem.put(decision.itemId(), decision));
            if (!byItem.keySet().equals(round.resultVersionIds().keySet())) {
                throw new GovernanceValidationException("确认决定尚未覆盖全部治理项");
            }
            round.resultVersionIds().forEach((itemId, resultId) -> {
                var decision = byItem.get(itemId);
                if (decision.resultVersionId() != resultId) {
                    throw new GovernanceConflictException("确认决定对应的治理结果已变化");
                }
                requireSubmittedRoundResult(round, requireRoundItem(round, itemId));
            });
            var statuses = new LinkedHashMap<Long, GovernanceItemStatus>();
            byItem.forEach((itemId, decision) -> statuses.put(itemId,
                    decision.decision() == Decision.APPROVED
                            ? GovernanceItemStatus.CONFIRMED
                            : GovernanceItemStatus.REWORK_REQUIRED));
            var target = decisions.stream().anyMatch(decision -> decision.decision() == Decision.REJECTED)
                    ? GovernanceTaskStatus.REWORK_REQUIRED
                    : GovernanceTaskStatus.PENDING_ACCEPTANCE;
            executionStore.updateItemStatuses(statuses);
            var updatedTask = taskStore.update(copyWithStatus(task, task.status().moveTo(target)), task.version());
            confirmationStore.completeRound(roundId, expectedRoundVersion, Instant.now(clock));
            var approved = (int) decisions.stream()
                    .filter(decision -> decision.decision() == Decision.APPROVED).count();
            var result = new CompletionResult(
                    taskId, roundId, updatedTask.status(), decisions.size(), approved,
                    rate(decisions.size(), round.resultVersionIds().size()),
                    rate(approved, round.resultVersionIds().size()), decisions);
            if (auditService != null) {
                auditService.record(taskId, null, "CONFIRMATION_ROUND", roundId, "CONFIRMATION_COMPLETED",
                        round.governanceRound(), decisions.getFirst().confirmerUserId(),
                        "{\"status\":\"PENDING\",\"version\":" + expectedRoundVersion + "}",
                        "{\"status\":\"COMPLETED\",\"version\":" + (expectedRoundVersion + 1)
                                + ",\"approved\":" + approved + ",\"total\":" + decisions.size() + "}");
            }
            return result;
        }
    }

    private ConfirmationView view(GovernanceConfirmationRound round) {
        var decisions = confirmationStore.decisions(round.id());
        var items = round.resultVersionIds().entrySet().stream().map(entry -> {
            var item = requireRoundItem(round, entry.getKey());
            var result = requireSubmittedRoundResult(round, item);
            var responsibility = responsibilityPort.currentResponsibility(item.assetId()).orElse(null);
            return new ConfirmationItem(
                    item.id(), item.assetId(), entry.getValue(), result.field().name(),
                    responsibility == null ? "" : responsibility.responsibleUserId(),
                    responsibility == null ? "" : responsibility.responsibilityScope());
        }).toList();
        var approved = (int) decisions.stream()
                .filter(decision -> decision.decision() == Decision.APPROVED).count();
        return new ConfirmationView(
                round, items, decisions, decisions.size(), approved,
                rate(decisions.size(), items.size()), rate(approved, items.size()));
    }

    private GovernanceConfirmationRound requirePending(long roundId) {
        var round = confirmationStore.round(roundId);
        if (round.status() != Status.PENDING) throw new GovernanceConflictException("确认轮次已经完成");
        return round;
    }

    private GovernanceItem requireRoundItem(GovernanceConfirmationRound round, long itemId) {
        if (!round.resultVersionIds().containsKey(itemId)) {
            throw new GovernanceValidationException("治理项不属于当前确认轮次");
        }
        var item = executionStore.item(itemId);
        if (item.taskId() != round.taskId()) {
            throw new GovernanceValidationException("治理项不属于当前确认任务");
        }
        return item;
    }

    private com.tianshu.assets.governance.execution.domain.GovernanceResultVersion requireSubmittedRoundResult(
            GovernanceConfirmationRound round, GovernanceItem item) {
        var result = executionStore.currentResult(item.id());
        if (result == null || result.status() != GovernanceResultStatus.SUBMITTED
                || !Objects.equals(round.resultVersionIds().get(item.id()), result.id())) {
            throw new GovernanceConflictException("当前确认结果已变化");
        }
        return result;
    }

    private AssetResponsibilityPort.AssetResponsibility requireResponsible(
            GovernanceItem item, String confirmerUserId) {
        var responsibility = responsibilityPort.currentResponsibility(item.assetId())
                .orElseThrow(() -> new GovernanceValidationException("资产没有当前有效责任人"));
        if (!responsibility.responsibleUserId().equals(confirmerUserId)) {
            throw new GovernanceValidationException("确认人不是资产当前有效责任人");
        }
        return responsibility;
    }

    private GovernanceTask copyWithStatus(GovernanceTask task, GovernanceTaskStatus status) {
        return new GovernanceTask(
                task.id(), task.taskNumber(), task.name(), task.actionType(), task.issueType(),
                task.ownerUserId(), task.ownerName(), task.assigneeId(), task.dueDate(), status,
                task.currentRound(), task.workflowVersion(), task.scopeSnapshotId(),
                task.qualityPolicySnapshotId(), task.legacyTotal(), task.legacyCompleted(), task.version());
    }

    private double rate(int numerator, int denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }

    public record DecisionCommand(
            Decision decision, String comment, long decisionVersion, String confirmerUserId) {}

    public record ConfirmationItem(
            long itemId,
            long assetId,
            long resultVersionId,
            String resultType,
            String responsibleUserId,
            String responsibilityScope) {}

    public record ConfirmationView(
            GovernanceConfirmationRound round,
            List<ConfirmationItem> items,
            List<GovernanceConfirmationDecision> decisions,
            int coveredCount,
            int approvedCount,
            double coverageRate,
            double approvalRate) {
        public ConfirmationView {
            items = List.copyOf(items);
            decisions = List.copyOf(decisions);
        }
    }

    public record CompletionResult(
            long taskId,
            long roundId,
            GovernanceTaskStatus taskStatus,
            int coveredCount,
            int approvedCount,
            double coverageRate,
            double approvalRate,
            List<GovernanceConfirmationDecision> decisions) {
        public CompletionResult {
            decisions = List.copyOf(decisions);
        }
    }

    private record BatchContext(
            GovernanceItem item, long resultVersionId, String resultType, String responsibilityScope) {}
}
