package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.mapping.application.GovernanceMappingRuleStore;
import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import com.tianshu.assets.governance.mapping.domain.GovernanceMappingRule;
import com.tianshu.assets.governance.mapping.domain.GovernanceMappingStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryGovernanceMappingRuleStore implements GovernanceMappingRuleStore {
    private final Map<Long, GovernanceMappingRule> rules = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1000);

    @Override public List<GovernanceMappingRule> findAll() { return new ArrayList<>(rules.values()); }
    @Override public Optional<GovernanceMappingRule> findById(long id) { return Optional.ofNullable(rules.get(id)); }

    @Override public synchronized GovernanceMappingRule create(GovernanceMappingRule rule) {
        var id = nextId.getAndIncrement();
        var created = copy(rule, id, rule.status(), rule.version());
        rules.put(id, created);
        return created;
    }

    @Override public synchronized GovernanceMappingRule confirm(long id, long expectedVersion, String userId, String userName, String comment, Instant at) {
        var current = require(id, expectedVersion);
        var updated = new GovernanceMappingRule(current.id(), current.standardId(), current.standardCode(), current.standardVersion(), current.ruleVersion(),
                current.sourceDimension(), current.sourceValue(), current.targetDictionaryCategory(), current.targetDictionaryItemId(), current.targetCode(), current.targetName(), current.scope(), current.ambiguous(), comment, userId, userName, at,
                current.usageCount(), current.matchedAssetCount(), current.affectedAssetCount(), GovernanceMappingStatus.CONFIRMED, current.version() + 1, current.createdAt(), at);
        rules.put(id, updated); return updated;
    }

    @Override public synchronized GovernanceMappingRule disable(long id, long expectedVersion, Instant at) {
        var current = require(id, expectedVersion);
        var updated = copy(current, id, GovernanceMappingStatus.DISABLED, current.version() + 1);
        updated = new GovernanceMappingRule(updated.id(), updated.standardId(), updated.standardCode(), updated.standardVersion(), updated.ruleVersion(), updated.sourceDimension(), updated.sourceValue(), updated.targetDictionaryCategory(), updated.targetDictionaryItemId(), updated.targetCode(), updated.targetName(), updated.scope(), updated.ambiguous(), updated.confirmationComment(), updated.confirmedByUserId(), updated.confirmedByName(), updated.confirmedAt(), updated.usageCount(), updated.matchedAssetCount(), updated.affectedAssetCount(), updated.status(), updated.version(), updated.createdAt(), at);
        rules.put(id, updated); return updated;
    }

    private GovernanceMappingRule require(long id, long expectedVersion) {
        var current = findById(id).orElseThrow(() -> new GovernanceConflictException("映射规则不存在"));
        if (current.version() != expectedVersion) throw new GovernanceVersionConflictException("映射规则已被其他用户更新，请刷新后重试");
        return current;
    }

    private GovernanceMappingRule copy(GovernanceMappingRule s, long id, GovernanceMappingStatus status, long version) {
        return new GovernanceMappingRule(id, s.standardId(), s.standardCode(), s.standardVersion(), s.ruleVersion(), s.sourceDimension(), s.sourceValue(), s.targetDictionaryCategory(), s.targetDictionaryItemId(), s.targetCode(), s.targetName(), s.scope(), s.ambiguous(), s.confirmationComment(), s.confirmedByUserId(), s.confirmedByName(), s.confirmedAt(), s.usageCount(), s.matchedAssetCount(), s.affectedAssetCount(), status, version, s.createdAt(), Instant.now());
    }
}
