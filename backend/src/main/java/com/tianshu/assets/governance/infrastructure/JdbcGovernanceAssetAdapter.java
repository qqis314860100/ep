package com.tianshu.assets.governance.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.asset.domain.AssetStatus;
import com.tianshu.assets.governance.acceptance.application.GovernanceAssetPort;
import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile({"local", "oceanbase"})
@ConditionalOnProperty(name = "asset.governance-schema-enabled", havingValue = "true")
public class JdbcGovernanceAssetAdapter implements GovernanceAssetPort {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcGovernanceAssetAdapter(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public GovernanceAssetSnapshot snapshot(long assetId) {
        return jdbcClient.sql("SELECT status, version FROM asset_package_ext WHERE drawing_id = :assetId")
                .param("assetId", assetId)
                .query((rs, ignored) -> new GovernanceAssetSnapshot(
                        assetId, AssetStatus.valueOf(rs.getString("status")), rs.getLong("version")))
                .optional().orElseThrow(() -> new IllegalArgumentException("资产扩展记录不存在：" + assetId));
    }

    @Override
    @Transactional
    public ApplyOutcome applyFieldResult(
            long itemId,
            long assetId,
            GovernanceField field,
            String proposedValueJson,
            long expectedAssetVersion,
            String actorUserId) {
        var action = "GOVERNANCE_FIELD_APPLIED:" + itemId;
        var existing = jdbcClient.sql("""
                SELECT COUNT(*) FROM asset_audit_ext
                WHERE drawing_id = :assetId AND action = :action
                """).param("assetId", assetId).param("action", action).query(Long.class).single();
        if (existing > 0) return new ApplyOutcome(snapshot(assetId).version(), "字段已应用");
        var root = parse(proposedValueJson);
        var updated = switch (field) {
            case DESCRIPTION -> updatePackage(
                    assetId, expectedAssetVersion, "standard_description", text(root, "description"));
            case SPECIALTIES -> updatePackage(
                    assetId, expectedAssetVersion, "standard_specialties", json(root.get("specialtyItemIds")));
            case OWNER -> applyOwner(assetId, expectedAssetVersion, root);
            case SCOPE -> applyScopes(assetId, expectedAssetVersion, root, itemId);
        };
        if (updated != 1) throw new GovernanceVersionConflictException("资产版本已变化，无法正式应用");
        var summary = json(Map.of("itemId", itemId, "field", field.name()));
        jdbcClient.sql("""
                INSERT INTO asset_audit_ext (drawing_id, actor_user_id, action, payload_json)
                VALUES (:assetId, :actor, :action, :summary)
                """).param("assetId", assetId).param("actor", actorUserId)
                .param("action", action).param("summary", summary).update();
        return new ApplyOutcome(expectedAssetVersion + 1, "字段 " + field.name() + " 已应用");
    }

    @Override
    public boolean meetsAllActiveStandards(long assetId) {
        return jdbcClient.sql("""
                SELECT COUNT(*) FROM asset_package_ext package
                WHERE package.drawing_id = :assetId
                  AND package.standard_description IS NOT NULL
                  AND package.standard_specialties IS NOT NULL
                  AND EXISTS (SELECT 1 FROM asset_responsibility_ext owner
                              WHERE owner.drawing_id = package.drawing_id AND owner.active = 1)
                  AND EXISTS (SELECT 1 FROM asset_scope_ext scope
                              WHERE scope.drawing_id = package.drawing_id
                                AND JSON_UNQUOTE(JSON_EXTRACT(scope.source_value_json, '$.source')) = 'GOVERNANCE')
                """).param("assetId", assetId).query(Long.class).single() == 1;
    }

    @Override
    public void markStandardized(long assetId, long expectedAssetVersion, String actorUserId) {
        var updated = jdbcClient.sql("""
                UPDATE asset_package_ext
                SET status = 'STANDARDIZED', version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
                WHERE drawing_id = :assetId AND version = :expectedVersion
                """).param("assetId", assetId).param("expectedVersion", expectedAssetVersion).update();
        if (updated != 1) throw new GovernanceVersionConflictException("资产版本已变化，无法标记为已标准化");
    }

    private int updatePackage(long assetId, long expectedVersion, String column, String value) {
        if (!java.util.Set.of("standard_description", "standard_specialties").contains(column)) {
            throw new IllegalArgumentException("不支持的资产扩展列");
        }
        return jdbcClient.sql("UPDATE asset_package_ext SET " + column + " = :value, "
                        + "version = version + 1, updated_at = CURRENT_TIMESTAMP(6) "
                        + "WHERE drawing_id = :assetId AND version = :expectedVersion")
                .param("value", value).param("assetId", assetId)
                .param("expectedVersion", expectedVersion).update();
    }

    private int applyOwner(long assetId, long expectedVersion, JsonNode root) {
        var current = snapshot(assetId);
        if (current.version() != expectedVersion) return 0;
        jdbcClient.sql("UPDATE asset_responsibility_ext SET active = 0 WHERE drawing_id = :assetId AND active = 1")
                .param("assetId", assetId).update();
        jdbcClient.sql("""
                INSERT INTO asset_responsibility_ext
                    (drawing_id, owner_user_id, owner_name, source, active)
                VALUES (:assetId, :ownerUserId, :ownerName, 'GOVERNANCE', 1)
                """).param("assetId", assetId).param("ownerUserId", text(root, "ownerUserId"))
                .param("ownerName", text(root, "ownerName")).update();
        return incrementVersion(assetId, expectedVersion);
    }

    private int applyScopes(long assetId, long expectedVersion, JsonNode root, long itemId) {
        var current = snapshot(assetId);
        if (current.version() != expectedVersion) return 0;
        var scopes = root.get("scopes");
        if (scopes == null || !scopes.isArray() || scopes.isEmpty()) {
            throw new GovernanceConflictException("治理适用范围结果不合法");
        }
        for (var scope : scopes) {
            jdbcClient.sql("""
                    INSERT INTO asset_scope_ext
                        (drawing_id, platform_family, platform_variant, product_line, base_name,
                         production_line, process_section, source_value_json)
                    VALUES (:assetId, :platformFamily, :platformVariant, :productLine, :baseName,
                            :productionLine, :processSection, :sourceValue)
                    """).param("assetId", assetId)
                    .param("platformFamily", text(scope, "platformFamily"))
                    .param("platformVariant", text(scope, "platformVariant"))
                    .param("productLine", text(scope, "productLine"))
                    .param("baseName", text(scope, "base"))
                    .param("productionLine", text(scope, "productionLine"))
                    .param("processSection", text(scope, "processSection"))
                    .param("sourceValue", json(Map.of("source", "GOVERNANCE", "itemId", itemId)))
                    .update();
        }
        return incrementVersion(assetId, expectedVersion);
    }

    private int incrementVersion(long assetId, long expectedVersion) {
        return jdbcClient.sql("""
                UPDATE asset_package_ext SET version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
                WHERE drawing_id = :assetId AND version = :expectedVersion
                """).param("assetId", assetId).param("expectedVersion", expectedVersion).update();
    }

    private JsonNode parse(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new GovernanceConflictException("治理结果 JSON 格式不正确");
        }
    }

    private String text(JsonNode root, String field) {
        var value = root.get(field);
        if (value == null || !value.isTextual()) throw new GovernanceConflictException("治理结果字段不完整");
        return value.asText();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("治理扩展值无法序列化", exception);
        }
    }
}
