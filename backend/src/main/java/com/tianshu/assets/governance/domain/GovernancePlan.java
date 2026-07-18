package com.tianshu.assets.governance.domain;

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
        List<Long> dependencyIds) {

    public GovernancePlan(long id, long taskId, String title, String status, LocalDate completedAt) {
        this(id, taskId, title, status, completedAt, null, null, null, null, 0, 0, "项", null, List.of());
    }

    public GovernancePlan {
        if (plannedQuantity < 0 || completedQuantity < 0 || completedQuantity > plannedQuantity) {
            throw new IllegalArgumentException("计划数量不合法");
        }
        if (quantityUnit == null || quantityUnit.isBlank()) quantityUnit = "项";
        if (dependencyIds == null) dependencyIds = List.of();
    }
}
