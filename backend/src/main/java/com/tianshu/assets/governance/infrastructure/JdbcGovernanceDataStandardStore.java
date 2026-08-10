package com.tianshu.assets.governance.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import com.tianshu.assets.governance.standard.application.GovernanceDataStandardStore;
import com.tianshu.assets.governance.standard.domain.GovernanceDataStandard;
import com.tianshu.assets.governance.standard.domain.GovernanceStandardImpactReview;
import com.tianshu.assets.governance.standard.domain.GovernanceStandardStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile({"local", "oceanbase"})
@ConditionalOnProperty(name = "asset.governance-schema-enabled", havingValue = "true")
public class JdbcGovernanceDataStandardStore extends JdbcGovernanceSupport
        implements GovernanceDataStandardStore {

    public JdbcGovernanceDataStandardStore(
            JdbcClient jdbc, ObjectMapper json,
            @Value("${asset.database-writes-enabled:false}") boolean writable) {
        super(jdbc, json, writable);
    }

    @Override
    public List<GovernanceDataStandard> findAll() {
        return jdbc.sql("SELECT standard_json FROM governance_data_standard ORDER BY standard_code, standard_version DESC")
                .query(String.class).list().stream().map(value -> decode(value, GovernanceDataStandard.class)).toList();
    }

    @Override
    public Optional<GovernanceDataStandard> findById(long id) {
        return jdbc.sql("SELECT standard_json FROM governance_data_standard WHERE id=:id")
                .param("id", id).query(String.class).optional()
                .map(value -> decode(value, GovernanceDataStandard.class));
    }

    @Override
    public Optional<GovernanceDataStandard> findByCodeAndStandardVersion(String code, long standardVersion) {
        return jdbc.sql("SELECT standard_json FROM governance_data_standard WHERE standard_code=:code AND standard_version=:standardVersion")
                .param("code", code).param("standardVersion", standardVersion)
                .query(String.class).optional().map(value -> decode(value, GovernanceDataStandard.class));
    }

    @Override
    public Optional<GovernanceDataStandard> findEnabledByCode(String code) {
        return jdbc.sql("SELECT standard_json FROM governance_data_standard WHERE standard_code=:code AND enabled=1 ORDER BY standard_version DESC LIMIT 1")
                .param("code", code).query(String.class).optional()
                .map(value -> decode(value, GovernanceDataStandard.class));
    }

    @Override
    public GovernanceDataStandard create(GovernanceDataStandard standard) {
        requireWritable();
        var key = new GeneratedKeyHolder();
        try {
            jdbc.sql("INSERT INTO governance_data_standard(standard_code,standard_version,enabled,standard_json) VALUES(:code,:standardVersion,0,'{}')")
                    .param("code", standard.standardCode()).param("standardVersion", standard.standardVersion())
                    .update(key, "id");
        } catch (DataIntegrityViolationException exception) {
            throw new GovernanceConflictException("同编码、同版本的数据标准已存在，不能覆盖");
        }
        var created = copy(standard, key.getKeyAs(Long.class), GovernanceStandardStatus.DRAFT,
                null, 0, 0, standard.createdAt(), standard.updatedAt());
        jdbc.sql("UPDATE governance_data_standard SET standard_json=:payload WHERE id=:id")
                .param("payload", encode(created)).param("id", created.id()).update();
        return created;
    }

    @Override
    @Transactional
    public GovernanceDataStandard enable(
            long id, long expectedVersion, long affectedAssetCount, Instant effectiveAt) {
        requireWritable();
        var current = requireVersion(id, expectedVersion);
        if (current.status() != GovernanceStandardStatus.DRAFT) {
            throw new GovernanceConflictException("只有草稿数据标准可以启用");
        }
        var oldEnabled = findEnabledByCode(current.standardCode()).orElse(null);
        if (oldEnabled != null) {
            var oldDisabled = copy(oldEnabled, oldEnabled.id(), GovernanceStandardStatus.DISABLED,
                    oldEnabled.effectiveAt(), oldEnabled.affectedAssetCount(), oldEnabled.version() + 1,
                    oldEnabled.createdAt(), effectiveAt);
            jdbc.sql("UPDATE governance_data_standard SET enabled=0,version=version+1,standard_json=:payload WHERE id=:id")
                    .param("payload", encode(oldDisabled)).param("id", oldEnabled.id()).update();
        }
        var enabled = copy(current, id, GovernanceStandardStatus.ENABLED, effectiveAt, affectedAssetCount,
                current.version() + 1, current.createdAt(), effectiveAt);
        var updated = jdbc.sql("UPDATE governance_data_standard SET enabled=1,version=version+1,standard_json=:payload WHERE id=:id AND version=:expectedVersion")
                .param("payload", encode(enabled)).param("id", id).param("expectedVersion", expectedVersion).update();
        requireUpdated(updated, () -> new GovernanceVersionConflictException("数据标准已被其他用户更新，请刷新后重试"));
        jdbc.sql("UPDATE governance_rule_catalog SET enabled=0 WHERE data_standard_id=:code AND enabled=1")
                .param("code", current.standardCode()).update();
        jdbc.sql("INSERT INTO governance_rule_catalog(data_standard_id,data_standard_version,field_rule_version,dictionary_versions_json,quality_policy_id,quality_policy_version,enabled) "
                        + "SELECT :code,:standardVersion,field_rule_version,dictionary_versions_json,quality_policy_id,quality_policy_version,1 FROM governance_rule_catalog WHERE data_standard_id=:sourceCode ORDER BY id DESC LIMIT 1")
                .param("code", current.standardCode()).param("standardVersion", current.standardVersion())
                .param("sourceCode", current.standardCode()).update();
        return enabled;
    }

    @Override
    public GovernanceDataStandard disable(long id, long expectedVersion, Instant updatedAt) {
        requireWritable();
        var current = requireVersion(id, expectedVersion);
        var disabled = copy(current, id, GovernanceStandardStatus.DISABLED, current.effectiveAt(),
                current.affectedAssetCount(), current.version() + 1, current.createdAt(), updatedAt);
        var updated = jdbc.sql("UPDATE governance_data_standard SET enabled=0,version=version+1,standard_json=:payload WHERE id=:id AND version=:expectedVersion")
                .param("payload", encode(disabled)).param("id", id).param("expectedVersion", expectedVersion).update();
        requireUpdated(updated, () -> new GovernanceVersionConflictException("数据标准已被其他用户更新，请刷新后重试"));
        return disabled;
    }

    @Override
    public GovernanceStandardImpactReview createImpactReview(
            long standardId, List<Long> assetIds, Instant createdAt) {
        requireWritable();
        var key = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO governance_standard_impact_review(standard_id,affected_asset_count,asset_ids_json,status,created_at) VALUES(:standardId,:affectedCount,:assetIds,'OPEN',:createdAt)")
                .param("standardId", standardId).param("affectedCount", assetIds.size())
                .param("assetIds", encode(assetIds))
                .param("createdAt", createdAt).update(key, "id");
        return new GovernanceStandardImpactReview(key.getKeyAs(Long.class), standardId, assetIds.size(), assetIds,
                GovernanceStandardImpactReview.Status.OPEN, createdAt);
    }

    @Override
    public List<GovernanceStandardImpactReview> findImpactReviews(long standardId) {
        return jdbc.sql("SELECT id,standard_id,affected_asset_count,asset_ids_json,status,created_at FROM governance_standard_impact_review WHERE standard_id=:standardId ORDER BY id DESC")
                .param("standardId", standardId).query((rs, rowNum) -> new GovernanceStandardImpactReview(
                        rs.getLong("id"), rs.getLong("standard_id"), rs.getLong("affected_asset_count"),
                        decodeLongList(rs.getString("asset_ids_json")),
                        GovernanceStandardImpactReview.Status.valueOf(rs.getString("status")),
                        rs.getTimestamp("created_at").toInstant())).list();
    }

    private GovernanceDataStandard requireVersion(long id, long expectedVersion) {
        var current = findById(id).orElseThrow(() -> new GovernanceConflictException("数据标准已不存在"));
        if (current.version() != expectedVersion) {
            throw new GovernanceVersionConflictException("数据标准已被其他用户更新，请刷新后重试");
        }
        return current;
    }

    private GovernanceDataStandard copy(
            GovernanceDataStandard source, long id, GovernanceStandardStatus status, Instant effectiveAt,
            long affectedAssetCount, long version, Instant createdAt, Instant updatedAt) {
        return new GovernanceDataStandard(
                id, source.standardCode(), source.standardVersion(), source.name(), status,
                source.applicableAssetTypes(), source.ownerUserId(), source.ownerName(), effectiveAt,
                source.changeSummary(), affectedAssetCount, source.rules(), version, createdAt, updatedAt);
    }

    private List<Long> decodeLongList(String value) {
        try {
            return json.readValue(value, json.getTypeFactory().constructCollectionType(List.class, Long.class));
        } catch (Exception exception) {
            throw new IllegalStateException("标准影响资产清单数据损坏", exception);
        }
    }
}
