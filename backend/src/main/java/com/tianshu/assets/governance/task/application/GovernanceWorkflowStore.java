package com.tianshu.assets.governance.task.application;

import com.tianshu.assets.governance.execution.domain.GovernanceItem;
import com.tianshu.assets.governance.task.domain.GovernanceRuleSnapshot;
import com.tianshu.assets.governance.task.domain.GovernanceScopeItem;
import com.tianshu.assets.governance.task.domain.GovernanceScopeSnapshot;
import java.time.Instant;
import java.util.List;

public interface GovernanceWorkflowStore {

    FrozenWorkflow freeze(FreezeCommand command);

    void discard(long scopeSnapshotId);

    GovernanceScopeSnapshot scopeSnapshot(long scopeSnapshotId);

    List<GovernanceScopeItem> scopeItems(long scopeSnapshotId);

    List<GovernanceItem> items(long taskId);

    record FreezeCommand(
            long taskId,
            List<Long> claimedIssueIds,
            List<Long> assetIds,
            GovernanceRuleSnapshot ruleSnapshot,
            String createdBy,
            Instant frozenAt,
            List<GovernanceScopeItem> scopeItems,
            List<GovernanceItem> items) {
        public FreezeCommand {
            claimedIssueIds = List.copyOf(claimedIssueIds);
            assetIds = List.copyOf(assetIds);
            scopeItems = List.copyOf(scopeItems);
            items = List.copyOf(items);
        }
    }

    record FrozenWorkflow(long scopeSnapshotId, long qualityPolicySnapshotId) {}
}
