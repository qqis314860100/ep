package com.tianshu.assets.governance.audit.application;

import com.tianshu.assets.governance.acceptance.application.GovernanceAcceptanceStore;
import com.tianshu.assets.governance.acceptance.domain.GovernanceOperationJobItem;
import com.tianshu.assets.governance.confirmation.application.GovernanceConfirmationStore;
import com.tianshu.assets.governance.confirmation.domain.GovernanceConfirmationDecision;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionStore;
import com.tianshu.assets.governance.execution.domain.GovernanceResultStatus;
import com.tianshu.assets.governance.task.application.GovernanceTaskStore;
import com.tianshu.assets.governance.task.application.GovernanceWorkflowStore;
import com.tianshu.assets.governance.task.domain.GovernanceScopeSnapshot;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GovernanceReportService {

    private final GovernanceTaskStore taskStore;
    private final GovernanceWorkflowStore workflowStore;
    private final GovernanceExecutionStore executionStore;
    private final GovernanceConfirmationStore confirmationStore;
    private final GovernanceAcceptanceStore acceptanceStore;

    public GovernanceReportService(
            GovernanceTaskStore taskStore,
            GovernanceWorkflowStore workflowStore,
            GovernanceExecutionStore executionStore,
            GovernanceConfirmationStore confirmationStore,
            GovernanceAcceptanceStore acceptanceStore) {
        this.taskStore = taskStore;
        this.workflowStore = workflowStore;
        this.executionStore = executionStore;
        this.confirmationStore = confirmationStore;
        this.acceptanceStore = acceptanceStore;
    }

    public GovernanceReport report(long taskId) {
        var task = taskStore.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("治理任务不存在"));
        GovernanceScopeSnapshot scope = workflowStore.scopeSnapshotForTask(taskId);
        var confirmationRounds = confirmationStore.rounds(taskId);
        var acceptanceByRound = acceptanceStore.rounds(taskId).stream()
                .collect(java.util.stream.Collectors.toMap(round -> round.governanceRound(), round -> round));
        var rounds = confirmationRounds.stream().map(round -> {
            var decisions = confirmationStore.decisions(round.id());
            var acceptance = acceptanceByRound.get(round.governanceRound());
            return new RoundReport(
                    round.governanceRound(), decisions.size(),
                    (int) decisions.stream().filter(decision ->
                            decision.decision() == GovernanceConfirmationDecision.Decision.APPROVED).count(),
                    acceptance == null ? null : acceptance.status().name(),
                    decisions.stream().filter(decision -> !decision.comment().isBlank())
                            .map(GovernanceConfirmationDecision::comment).toList());
        }).toList();
        var items = executionStore.items(taskId).stream().map(item -> {
            var results = executionStore.resultsForItem(item.id());
            var original = results.isEmpty() ? "{}" : results.getFirst().originalValueJson();
            var proposed = results.isEmpty() ? "{}" : results.getLast().proposedValueJson();
            var applied = results.stream().filter(result -> result.status() == GovernanceResultStatus.APPLIED)
                    .reduce((first, second) -> second).map(result -> result.proposedValueJson()).orElse("{}");
            var decisions = confirmationRounds.stream()
                    .flatMap(round -> confirmationStore.decisions(round.id()).stream())
                    .filter(decision -> decision.itemId() == item.id()).toList();
            var reworkReasons = results.stream().map(result -> result.reworkReason())
                    .filter(reason -> !reason.isBlank()).distinct().toList();
            return new ItemReport(
                    item.id(), item.assetId(), item.targetField().name(), original, proposed, applied,
                    decisions, reworkReasons);
        }).toList();
        var jobs = acceptanceStore.applicationJobs(taskId);
        var jobItems = jobs.stream().flatMap(job -> job.items().stream()).toList();
        var application = new ApplicationSummary(
                jobItems.size(),
                (int) jobItems.stream().filter(item -> item.status() == GovernanceOperationJobItem.Status.SUCCEEDED)
                        .count(),
                (int) jobItems.stream().filter(item -> item.status() == GovernanceOperationJobItem.Status.FAILED)
                        .count());
        var completed = (int) items.stream().filter(item -> !item.appliedValueJson().equals("{}")).count();
        return new GovernanceReport(
                task.id(), task.taskNumber(), task.status(), scope, rounds, items,
                new ProgressSummary(items.size(), completed), application);
    }

    public record GovernanceReport(
            long taskId,
            String taskNumber,
            GovernanceTaskStatus status,
            GovernanceScopeSnapshot scopeSnapshot,
            List<RoundReport> rounds,
            List<ItemReport> items,
            ProgressSummary progress,
            ApplicationSummary applicationSummary) {
        public GovernanceReport {
            rounds = List.copyOf(rounds);
            items = List.copyOf(items);
        }
    }

    public record RoundReport(
            int governanceRound,
            int confirmationCount,
            int approvedCount,
            String acceptanceStatus,
            List<String> reworkReasons) {
        public RoundReport {
            reworkReasons = List.copyOf(reworkReasons);
        }
    }

    public record ItemReport(
            long itemId,
            long assetId,
            String field,
            String originalValueJson,
            String proposedValueJson,
            String appliedValueJson,
            List<GovernanceConfirmationDecision> confirmationDecisions,
            List<String> reworkReasons) {
        public ItemReport {
            confirmationDecisions = List.copyOf(confirmationDecisions);
            reworkReasons = List.copyOf(reworkReasons);
        }
    }

    public record ProgressSummary(int total, int completed) {}

    public record ApplicationSummary(int total, int succeeded, int failed) {}
}
