package com.tianshu.assets.governance.standard.application;

import com.tianshu.assets.governance.standard.domain.GovernanceDataStandard;
import com.tianshu.assets.governance.standard.domain.GovernanceStandardImpactReview;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GovernanceDataStandardStore {

    List<GovernanceDataStandard> findAll();

    Optional<GovernanceDataStandard> findById(long id);

    Optional<GovernanceDataStandard> findByCodeAndStandardVersion(String code, long standardVersion);

    Optional<GovernanceDataStandard> findEnabledByCode(String code);

    GovernanceDataStandard create(GovernanceDataStandard standard);

    GovernanceDataStandard enable(long id, long expectedVersion, long affectedAssetCount, Instant effectiveAt);

    GovernanceDataStandard disable(long id, long expectedVersion, Instant updatedAt);

    GovernanceStandardImpactReview createImpactReview(
            long standardId, List<Long> assetIds, Instant createdAt);

    List<GovernanceStandardImpactReview> findImpactReviews(long standardId);
}
