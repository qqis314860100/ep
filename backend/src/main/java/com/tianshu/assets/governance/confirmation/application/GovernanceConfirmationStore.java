package com.tianshu.assets.governance.confirmation.application;

import com.tianshu.assets.governance.confirmation.domain.GovernanceConfirmationDecision;
import com.tianshu.assets.governance.confirmation.domain.GovernanceConfirmationRound;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface GovernanceConfirmationStore {

    Optional<GovernanceConfirmationRound> currentRound(long taskId);

    GovernanceConfirmationRound round(long roundId);

    GovernanceConfirmationRound createRound(
            long taskId, int governanceRound, Map<Long, Long> resultVersionIds, Instant createdAt);

    void discardPendingRound(long roundId);

    List<GovernanceConfirmationDecision> decisions(long roundId);

    GovernanceConfirmationDecision insertDecision(GovernanceConfirmationDecision decision);

    List<GovernanceConfirmationDecision> insertDecisions(List<GovernanceConfirmationDecision> decisions);

    GovernanceConfirmationRound completeRound(long roundId, long expectedVersion, Instant completedAt);
}
