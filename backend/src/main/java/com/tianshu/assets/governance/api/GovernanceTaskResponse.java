package com.tianshu.assets.governance.api;

import com.tianshu.assets.governance.domain.GovernanceTask;
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
        String status) {

    public static GovernanceTaskResponse from(GovernanceTask task, String assigneeId) {
        return new GovernanceTaskResponse(task.id(), task.name(), task.scope(), task.owner(), assigneeId, task.total(),
                task.completed(), task.dueDate(), task.status().name());
    }
}
