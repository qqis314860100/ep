package com.tianshu.assets.governance.mapping.application;

import com.tianshu.assets.governance.mapping.domain.GovernanceMappingRule;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GovernanceMappingRuleStore {
    List<GovernanceMappingRule> findAll();
    Optional<GovernanceMappingRule> findById(long id);
    GovernanceMappingRule create(GovernanceMappingRule rule);
    GovernanceMappingRule confirm(long id, long expectedVersion, String userId, String userName, String comment, Instant at);
    GovernanceMappingRule disable(long id, long expectedVersion, Instant at);
}
