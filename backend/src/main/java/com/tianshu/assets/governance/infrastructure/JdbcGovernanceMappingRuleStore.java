package com.tianshu.assets.governance.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.mapping.application.GovernanceMappingRuleStore;
import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import com.tianshu.assets.governance.mapping.domain.GovernanceMappingRule;
import com.tianshu.assets.governance.mapping.domain.GovernanceMappingStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "oceanbase"})
@ConditionalOnProperty(name = "asset.governance-schema-enabled", havingValue = "true")
public class JdbcGovernanceMappingRuleStore extends JdbcGovernanceSupport implements GovernanceMappingRuleStore {
    public JdbcGovernanceMappingRuleStore(JdbcClient jdbc, ObjectMapper json,
            @Value("${asset.database-writes-enabled:false}") boolean writable) {
        super(jdbc, json, writable);
    }

    @Override public List<GovernanceMappingRule> findAll() {
        return jdbc.sql("SELECT payload_json FROM governance_mapping_rule ORDER BY source_dimension,source_value,rule_version DESC")
                .query(String.class).list().stream().map(value -> decode(value, GovernanceMappingRule.class)).toList();
    }
    @Override public Optional<GovernanceMappingRule> findById(long id) {
        return jdbc.sql("SELECT payload_json FROM governance_mapping_rule WHERE id=:id").param("id", id).query(String.class).optional().map(value -> decode(value, GovernanceMappingRule.class));
    }
    @Override public GovernanceMappingRule create(GovernanceMappingRule rule) {
        requireWritable(); var key = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO governance_mapping_rule(standard_id,standard_code,standard_version,rule_version,source_dimension,source_value,status,version,payload_json) VALUES(:standardId,:standardCode,:standardVersion,:ruleVersion,:dimension,:value,:status,0,:payload)")
                .param("standardId", rule.standardId()).param("standardCode", rule.standardCode()).param("standardVersion", rule.standardVersion()).param("ruleVersion", rule.ruleVersion())
                .param("dimension", rule.sourceDimension()).param("value", rule.sourceValue()).param("status", rule.status().name()).param("payload", encode(rule)).update(key, "id");
        var created = copy(rule, key.getKeyAs(Long.class), rule.version());
        jdbc.sql("UPDATE governance_mapping_rule SET payload_json=:payload WHERE id=:id").param("payload", encode(created)).param("id", created.id()).update();
        return created;
    }
    @Override public GovernanceMappingRule confirm(long id, long expectedVersion, String userId, String userName, String comment, Instant at) {
        requireWritable(); var current = require(id, expectedVersion);
        var updated = new GovernanceMappingRule(current.id(), current.standardId(), current.standardCode(), current.standardVersion(), current.ruleVersion(), current.sourceDimension(), current.sourceValue(), current.targetDictionaryCategory(), current.targetDictionaryItemId(), current.targetCode(), current.targetName(), current.scope(), current.ambiguous(), comment, userId, userName, at, current.usageCount(), current.matchedAssetCount(), current.affectedAssetCount(), GovernanceMappingStatus.CONFIRMED, current.version() + 1, current.createdAt(), at);
        save(updated, expectedVersion); return updated;
    }
    @Override public GovernanceMappingRule disable(long id, long expectedVersion, Instant at) {
        requireWritable(); var current = require(id, expectedVersion);
        var updated = new GovernanceMappingRule(current.id(), current.standardId(), current.standardCode(), current.standardVersion(), current.ruleVersion(), current.sourceDimension(), current.sourceValue(), current.targetDictionaryCategory(), current.targetDictionaryItemId(), current.targetCode(), current.targetName(), current.scope(), current.ambiguous(), current.confirmationComment(), current.confirmedByUserId(), current.confirmedByName(), current.confirmedAt(), current.usageCount(), current.matchedAssetCount(), current.affectedAssetCount(), GovernanceMappingStatus.DISABLED, current.version() + 1, current.createdAt(), at);
        save(updated, expectedVersion); return updated;
    }
    private GovernanceMappingRule require(long id, long version) {
        var current = findById(id).orElseThrow(() -> new GovernanceConflictException("映射规则不存在"));
        if (current.version() != version) throw new GovernanceVersionConflictException("映射规则已被其他用户更新，请刷新后重试");
        return current;
    }
    private void save(GovernanceMappingRule value, long expectedVersion) {
        int updated = jdbc.sql("UPDATE governance_mapping_rule SET status=:status,version=version+1,payload_json=:payload WHERE id=:id AND version=:version")
                .param("status", value.status().name()).param("payload", encode(value)).param("id", value.id()).param("version", expectedVersion).update();
        if (updated != 1) throw new GovernanceVersionConflictException("映射规则已被其他用户更新，请刷新后重试");
    }
    private GovernanceMappingRule copy(GovernanceMappingRule source, long id, long version) {
        return new GovernanceMappingRule(id, source.standardId(), source.standardCode(), source.standardVersion(), source.ruleVersion(), source.sourceDimension(), source.sourceValue(), source.targetDictionaryCategory(), source.targetDictionaryItemId(), source.targetCode(), source.targetName(), source.scope(), source.ambiguous(), source.confirmationComment(), source.confirmedByUserId(), source.confirmedByName(), source.confirmedAt(), source.usageCount(), source.matchedAssetCount(), source.affectedAssetCount(), source.status(), version, source.createdAt(), source.updatedAt());
    }
}
