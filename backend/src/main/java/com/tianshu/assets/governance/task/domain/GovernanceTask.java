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
        // 负责人（ownerUserId/ownerName）为创建后锁定字段，只能经移交专用通道变更；
        // 通用状态更新沿用原负责人，assigneeId 等可变字段取自请求。
        return new GovernanceTask(
                id, taskNumber, name, actionType, issueType, ownerUserId, ownerName,
                requested.assigneeId(), requested.dueDate(), requested.status(), requested.currentRound(),
                workflowVersion, requested.scopeSnapshotId(), requested.qualityPolicySnapshotId(),
                legacyTotal, legacyCompleted, nextVersion);
    }

    /** 生成移交后的任务副本：执行人与负责人整体换人，其余字段保持（版本由 store 推进）。 */
    public GovernanceTask reassignTo(String newOwnerUserId, String newOwnerName, long nextVersion) {
        return new GovernanceTask(
                id, taskNumber, name, actionType, issueType,
                newOwnerUserId, newOwnerName, newOwnerUserId,
                dueDate, status, currentRound, workflowVersion,
                scopeSnapshotId, qualityPolicySnapshotId,
                legacyTotal, legacyCompleted, nextVersion);
    }
}
