package com.tianshu.assets.governance.api;

import com.tianshu.assets.governance.task.domain.GovernanceTask;
import com.tianshu.assets.governance.task.domain.GovernanceWorkflowVersion;
import com.tianshu.assets.governance.task.application.GovernanceTaskApplicationService.PlanProjection;
import com.tianshu.assets.governance.task.application.GovernanceTaskApplicationService.TaskProjection;
import com.tianshu.assets.governance.task.domain.GovernanceProgress;
import com.tianshu.assets.governance.task.domain.GovernanceRuleSnapshot;
import com.tianshu.assets.governance.task.domain.GovernanceScopeSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record GovernanceTaskResponse(
        long id,
        String name,
        String scope,
        String owner,
        String assigneeId,
        int total,
        int completed,
        LocalDate dueDate,
        String status,
        String workflowVersion,
        int currentRound,
        long version,
        boolean editable,
        GovernanceProgress progress,
        int riskCount,
        List<PlanProjection> plans,
        GovernanceScopeSnapshot scopeSnapshot,
        GovernanceRuleSnapshot ruleSnapshot,
        Map<String, String> workbenchEntries) {

    public static GovernanceTaskResponse from(GovernanceTask task) {
        var legacy = task.workflowVersion() == GovernanceWorkflowVersion.LEGACY_PROGRESS;
        return new GovernanceTaskResponse(
                task.id(), task.name(), task.actionType(), task.ownerName(), task.assigneeId(),
                legacy ? task.legacyTotal() : 0,
                legacy ? task.legacyCompleted() : 0,
                task.dueDate(), task.status().name(), task.workflowVersion().name(), task.currentRound(),
                task.version(), !legacy, null, 0, List.of(), null, null, Map.of());
    }

    public static GovernanceTaskResponse from(TaskProjection projection) {
        var task = projection.task();
        var legacy = task.workflowVersion() == GovernanceWorkflowVersion.LEGACY_PROGRESS;
        return new GovernanceTaskResponse(
                task.id(), task.name(), task.actionType(), task.ownerName(), task.assigneeId(),
                projection.progress().total(), projection.progress().submitted(), task.dueDate(),
                task.status().name(), task.workflowVersion().name(), task.currentRound(), task.version(),
                !legacy, projection.progress(), projection.riskCount(), projection.plans(),
                projection.scopeSnapshot(), projection.ruleSnapshot(), projection.workbenchEntries());
    }
}
