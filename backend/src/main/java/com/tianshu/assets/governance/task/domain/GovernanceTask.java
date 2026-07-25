package com.tianshu.assets.governance.task.domain;

import java.time.LocalDate;

public record GovernanceTask(
        long id,
        String taskNumber,
        String name,
        String actionType,
        String issueType,
        String ownerUserId,
        String ownerName,
        String assigneeId,
        LocalDate dueDate,
        GovernanceTaskStatus status,
        int currentRound,
        GovernanceWorkflowVersion workflowVersion,
        Long scopeSnapshotId,
        Long qualityPolicySnapshotId,
        int legacyTotal,
        int legacyCompleted,
        long version) {

    public GovernanceTask {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("治理任务名称不能为空");
        if (status == null) throw new IllegalArgumentException("治理任务状态不能为空");
        if (workflowVersion == null) throw new IllegalArgumentException("治理流程版本不能为空");
        if (workflowVersion == GovernanceWorkflowVersion.CLOSED_LOOP_V1 && dueDate == null) {
            throw new IllegalArgumentException("计划完成日期不能为空");
        }
        if (currentRound < 0 || legacyTotal < 0 || legacyCompleted < 0 || legacyCompleted > legacyTotal) {
            throw new IllegalArgumentException("治理任务进度不合法");
        }
    }

    public GovernanceTask applyMutableState(GovernanceTask requested, long nextVersion) {
        if (requested.id() != id) throw new IllegalArgumentException("治理任务 ID 不匹配");
        return new GovernanceTask(
                id, taskNumber, name, actionType, issueType, ownerUserId, ownerName,
                requested.assigneeId(), requested.dueDate(), requested.status(), requested.currentRound(),
                workflowVersion, requested.scopeSnapshotId(), requested.qualityPolicySnapshotId(),
                legacyTotal, legacyCompleted, nextVersion);
    }
}
