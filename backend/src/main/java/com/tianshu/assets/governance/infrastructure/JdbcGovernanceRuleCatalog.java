package com.tianshu.assets.governance.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.governance.application.GovernanceValidationException;
import com.tianshu.assets.governance.task.application.GovernanceRuleCatalog;
import com.tianshu.assets.governance.task.domain.GovernanceRuleSnapshot;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "oceanbase"})
@ConditionalOnProperty(name = "asset.governance-schema-enabled", havingValue = "true")
public class JdbcGovernanceRuleCatalog implements GovernanceRuleCatalog {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public JdbcGovernanceRuleCatalog(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public GovernanceRuleSnapshot enabledSnapshot() {
        return jdbc.sql("SELECT id,data_standard_id,data_standard_version,field_rule_version,dictionary_versions_json,quality_policy_id,quality_policy_version FROM governance_rule_catalog WHERE enabled=1 ORDER BY id DESC LIMIT 1")
                .query((rs, rowNum) -> new GovernanceRuleSnapshot(
                        rs.getLong("id"), rs.getString("data_standard_id"),
                        rs.getLong("data_standard_version"), rs.getLong("field_rule_version"),
                        dictionaryVersions(rs.getString("dictionary_versions_json")),
                        rs.getString("quality_policy_id"), rs.getLong("quality_policy_version")))
                .optional()
                .orElseThrow(() -> new GovernanceValidationException(
                        "当前没有启用的数据标准，不能启动或执行治理任务"));
    }

    @Override
    public List<AssetScope> validScopes() {
        return jdbc.sql("SELECT platform_family,product_line,base_name,production_line,process_section,platform_family,platform_variant FROM asset_scope_ext WHERE active=1 ORDER BY id")
                .query((rs, rowNum) -> new AssetScope(
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7)))
                .list();
    }

    @Override
    public boolean isDataStandardEnabled(String standardCode, long standardVersion) {
        return jdbc.sql("SELECT COUNT(*) FROM governance_data_standard WHERE standard_code=:code AND standard_version=:standardVersion AND enabled=1")
                .param("code", standardCode).param("standardVersion", standardVersion)
                .query(Long.class).single() == 1;
    }

    private Map<String, Long> dictionaryVersions(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("治理规则目录数据损坏", exception);
        }
    }
}
