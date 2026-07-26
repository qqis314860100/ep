package com.tianshu.assets.governance.acceptance.application;

import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceRound;
import com.tianshu.assets.governance.acceptance.domain.GovernanceOperationJob;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public interface GovernanceAcceptanceStore {

    Optional<GovernanceAcceptanceRound> currentRound(long taskId);

    GovernanceAcceptanceRound round(long roundId);

    GovernanceAcceptanceRound createRound(GovernanceAcceptanceRound round);

    GovernanceAcceptanceRound updateRound(GovernanceAcceptanceRound round, long expectedVersion);

    GovernanceOperationJob createApplicationJob(
            long taskId,
            long acceptanceRoundId,
            Map<Long, Long> resultVersionIds,
            String requestedBy,
            Instant requestedAt);

    Optional<GovernanceOperationJob> applicationJob(long jobId);

    GovernanceOperationJob claimApplicationJob(long jobId, long expectedVersion);

    GovernanceOperationJob updateApplicationJob(GovernanceOperationJob job, long expectedVersion);
}
