package com.tianshu.assets.governance.execution.application;

import com.tianshu.assets.governance.execution.domain.GovernanceItem;
import com.tianshu.assets.governance.execution.domain.GovernanceItemStatus;
import com.tianshu.assets.governance.execution.domain.GovernanceResultVersion;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface GovernanceExecutionStore {

    GovernanceItem item(long itemId);

    List<GovernanceItem> items(long taskId);

    GovernanceResultVersion currentResult(long itemId);

    List<GovernanceResultVersion> resultsForItem(long itemId);

    GovernanceResultVersion saveDraft(SaveDraft command);

    GovernanceResultVersion submit(Submit command);

    GovernanceResultVersion openRework(OpenRework command);

    GovernanceItem updateItemStatus(long itemId, GovernanceItemStatus status, String reason);

    List<GovernanceItem> updateItemStatuses(Map<Long, GovernanceItemStatus> statuses);

    record SaveDraft(
            long itemId,
            long expectedItemVersion,
            long expectedAssetVersion,
            GovernanceField field,
            String originalValueJson,
            String proposedValueJson,
            long standardVersion,
            Map<String, Long> dictionaryVersions,
            String actorUserId,
            Instant savedAt) {
        public SaveDraft {
            dictionaryVersions = Map.copyOf(dictionaryVersions);
        }
    }

    record Submit(
            long itemId,
            long resultVersionId,
            long expectedResultVersion,
            String actorUserId,
            Instant submittedAt) {}

    record OpenRework(
            long itemId,
            long expectedItemVersion,
            int governanceRound,
            String reason,
            String actorUserId,
            Instant openedAt) {}
}
