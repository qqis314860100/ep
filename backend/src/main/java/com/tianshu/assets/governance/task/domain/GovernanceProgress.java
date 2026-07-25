package com.tianshu.assets.governance.task.domain;

import com.tianshu.assets.governance.execution.domain.GovernanceItemStatus;
import java.util.List;
import java.util.function.Predicate;

public record GovernanceProgress(
        int total,
        int submitted,
        int confirmed,
        int accepted,
        int blocked,
        int reworkRequired) {

    public static GovernanceProgress from(List<GovernanceItemStatus> itemStatuses) {
        return new GovernanceProgress(
                itemStatuses.size(),
                count(itemStatuses, GovernanceItemStatus::countsAsSubmitted),
                count(itemStatuses, GovernanceItemStatus::countsAsConfirmed),
                count(itemStatuses, status -> status == GovernanceItemStatus.ACCEPTED),
                count(itemStatuses, status -> status == GovernanceItemStatus.BLOCKED),
                count(itemStatuses, status -> status == GovernanceItemStatus.REWORK_REQUIRED));
    }

    private static int count(List<GovernanceItemStatus> itemStatuses, Predicate<GovernanceItemStatus> predicate) {
        return (int) itemStatuses.stream().filter(predicate).count();
    }
}
