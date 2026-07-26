package com.tianshu.assets.governance.acceptance.application;

import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceRound;
import java.util.Optional;

public interface GovernanceAcceptanceStore {

    Optional<GovernanceAcceptanceRound> currentRound(long taskId);

    GovernanceAcceptanceRound round(long roundId);

    GovernanceAcceptanceRound createRound(GovernanceAcceptanceRound round);

    GovernanceAcceptanceRound updateRound(GovernanceAcceptanceRound round, long expectedVersion);
}
