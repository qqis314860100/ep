package com.tianshu.assets.governance.task.domain;

import java.time.LocalDate;
import java.util.List;

public record GovernancePlan(
        long id,
        long taskId,
        int sequence,
        String title,
        GovernancePlanStatus planStatus,
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
        String responsibleUserId,
        List<Long> dependencyIds,
        List<Long> issueIds,
        long version) {

    public GovernancePlan {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("计划名称不能为空");
        if (plannedQuantity < 0 || completedQuantity < 0 || completedQuantity > plannedQuantity) {
            throw new IllegalArgumentException("计划数量不合法");
        }
        if (quantityUnit == null || quantityUnit.isBlank()) quantityUnit = "项";
        dependencyIds = dependencyIds == null ? List.of() : List.copyOf(dependencyIds);
        issueIds = issueIds == null ? List.of() : List.copyOf(issueIds);
        if (planStatus == null) planStatus = parseStatus(status);
        if (status == null || status.isBlank()) status = planStatus.name();
        if (responsibleUserId == null) responsibleUserId = assigneeId;
        if (assigneeId == null) assigneeId = responsibleUserId;
    }

    public GovernancePlan(
            long id, long taskId, String title, String status, LocalDate completedAt,
            LocalDate plannedStart, LocalDate plannedEnd, LocalDate actualStart, LocalDate actualEnd,
            int plannedQuantity, int completedQuantity, String quantityUnit, String assigneeId,
            List<Long> dependencyIds, long version) {
        this(id, taskId, 0, title, parseStatus(status), status, completedAt, plannedStart, plannedEnd,
                actualStart, actualEnd, plannedQuantity, completedQuantity, quantityUnit, assigneeId,
                assigneeId, dependencyIds, List.of(), version);
    }

    public static GovernancePlan closedLoop(
            long id, long taskId, int sequence, String title, String responsibleUserId,
            LocalDate startDate, LocalDate dueDate, List<Long> dependencyIds,
            List<Long> issueIds, long version) {
        var uniqueIssueIds = issueIds == null ? List.<Long>of() : List.copyOf(issueIds);
        return new GovernancePlan(
                id, taskId, sequence, title, GovernancePlanStatus.NOT_STARTED,
                GovernancePlanStatus.NOT_STARTED.name(), null, startDate, dueDate, null, null,
                new java.util.LinkedHashSet<>(uniqueIssueIds).size(), 0, "个字段",
                responsibleUserId, responsibleUserId, dependencyIds, uniqueIssueIds, version);
    }

    public String name() {
        return title;
    }

    public LocalDate startDate() {
        return plannedStart;
    }

    public LocalDate dueDate() {
        return plannedEnd;
    }

    private static GovernancePlanStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "TODO".equals(status)) return GovernancePlanStatus.NOT_STARTED;
        try {
            return GovernancePlanStatus.valueOf(status);
        } catch (IllegalArgumentException ignored) {
            return GovernancePlanStatus.NOT_STARTED;
        }
    }
}
