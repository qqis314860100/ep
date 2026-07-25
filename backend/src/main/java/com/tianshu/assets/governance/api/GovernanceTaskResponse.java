package com.tianshu.assets.governance.api;

import com.tianshu.assets.governance.task.domain.GovernanceTask;
import com.tianshu.assets.governance.task.domain.GovernanceWorkflowVersion;
import java.time.LocalDate;

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
        boolean editable) {

    public static GovernanceTaskResponse from(GovernanceTask task) {
        var legacy = task.workflowVersion() == GovernanceWorkflowVersion.LEGACY_PROGRESS;
        return new GovernanceTaskResponse(
                task.id(), task.name(), task.actionType(), task.ownerName(), task.assigneeId(),
                legacy ? task.legacyTotal() : 0,
                legacy ? task.legacyCompleted() : 0,
                task.dueDate(), task.status().name(), task.workflowVersion().name(), task.currentRound(),
                task.version(), !legacy);
    }
}
