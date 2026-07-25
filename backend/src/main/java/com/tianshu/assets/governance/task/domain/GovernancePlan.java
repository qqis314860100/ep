package com.tianshu.assets.governance.task.domain;

import java.time.LocalDate;
import java.util.List;

public record GovernancePlan(
        long id,
        long taskId,
        String title,
        String status,
        LocalDate completedAt,
        LocalDate plannedStart,
        LocalDate plannedEnd,
        LocalDate actualStart,
        LocalDate actualEnd,
        int plannedQuantity,
        int completedQuantity,
        String quantityUnit,
        String assigneeId,
        List<Long> dependencyIds,
        long version) {

    public GovernancePlan {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("计划名称不能为空");
        if (plannedQuantity < 0 || completedQuantity < 0 || completedQuantity > plannedQuantity) {
            throw new IllegalArgumentException("计划数量不合法");
        }
        if (quantityUnit == null || quantityUnit.isBlank()) quantityUnit = "项";
        dependencyIds = dependencyIds == null ? List.of() : List.copyOf(dependencyIds);
    }
}
