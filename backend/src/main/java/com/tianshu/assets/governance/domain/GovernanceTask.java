package com.tianshu.assets.governance.domain;

import java.time.LocalDate;

public record GovernanceTask(
        long id,
        String name,
        String scope,
        String owner,
        int total,
        int completed,
        LocalDate dueDate,
        GovernanceTaskStatus status) {

    public GovernanceTask {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("治理任务名称不能为空");
        if (scope == null) scope = "";
        if (owner == null) owner = "";
        if (total < 0 || completed < 0 || completed > total) {
            throw new IllegalArgumentException("治理任务进度不合法");
        }
        if (dueDate == null) throw new IllegalArgumentException("计划完成日期不能为空");
        if (status == null) status = completed == total ? GovernanceTaskStatus.COMPLETED : GovernanceTaskStatus.IN_PROGRESS;
    }
}
