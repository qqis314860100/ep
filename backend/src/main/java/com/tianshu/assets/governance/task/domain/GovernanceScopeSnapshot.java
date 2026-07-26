package com.tianshu.assets.governance.task.domain;

import java.time.Instant;
import java.util.List;

public record GovernanceScopeSnapshot(
        long id,
        long taskId,
        List<Long> claimedIssueIds,
        List<Long> assetIds,
        GovernanceRuleSnapshot ruleSnapshot,
        String createdBy,
        Instant frozenAt,
        int itemCount) {

    public GovernanceScopeSnapshot {
        claimedIssueIds = claimedIssueIds == null ? List.of() : List.copyOf(claimedIssueIds);
        assetIds = assetIds == null ? List.of() : List.copyOf(assetIds);
        if (ruleSnapshot == null) throw new IllegalArgumentException("规则快照不能为空");
        if (createdBy == null || createdBy.isBlank()) throw new IllegalArgumentException("快照创建人不能为空");
        if (frozenAt == null) throw new IllegalArgumentException("快照固化时间不能为空");
        if (itemCount < 0) throw new IllegalArgumentException("快照治理项数量不合法");
    }
}
