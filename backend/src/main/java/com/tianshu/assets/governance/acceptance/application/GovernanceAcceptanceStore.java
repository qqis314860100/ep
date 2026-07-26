package com.tianshu.assets.governance.acceptance.application;

import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceRound;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public interface GovernanceAcceptanceStore {

    Optional<GovernanceAcceptanceRound> currentRound(long taskId);

    GovernanceAcceptanceRound round(long roundId);

    GovernanceAcceptanceRound createRound(GovernanceAcceptanceRound round);

    GovernanceAcceptanceRound updateRound(GovernanceAcceptanceRound round, long expectedVersion);

    ApplicationJobRequest createApplicationJob(
            long taskId,
            long acceptanceRoundId,
            Map<Long, Long> resultVersionIds,
            String requestedBy,
            Instant requestedAt);

    Optional<ApplicationJobRequest> applicationJob(long jobId);

    record ApplicationJobRequest(
            long id,
            long taskId,
            long acceptanceRoundId,
            Map<Long, Long> resultVersionIds,
            String requestedBy,
            Instant requestedAt) {
        public ApplicationJobRequest {
            resultVersionIds = Map.copyOf(resultVersionIds);
        }
    }
}
