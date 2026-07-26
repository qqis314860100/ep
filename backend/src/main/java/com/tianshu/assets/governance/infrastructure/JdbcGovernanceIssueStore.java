package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.application.GovernanceTaskStateException;
import com.tianshu.assets.governance.issue.application.GovernanceIssueStore;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import com.tianshu.assets.governance.issue.domain.GovernanceIssue;
import com.tianshu.assets.governance.issue.domain.GovernanceIssueStatus;
import com.tianshu.assets.governance.task.application.GovernanceStorageException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile({"local", "oceanbase"})
@ConditionalOnProperty(name = "asset.governance-schema-enabled", havingValue = "true")
public class JdbcGovernanceIssueStore implements GovernanceIssueStore {

    private final JdbcClient jdbcClient;
    private final boolean databaseWritesEnabled;

    public JdbcGovernanceIssueStore(
            JdbcClient jdbcClient,
            @Value("${asset.database-writes-enabled:false}") boolean databaseWritesEnabled) {
        this.jdbcClient = jdbcClient;
        this.databaseWritesEnabled = databaseWritesEnabled;
    }

    @Override
    public List<GovernanceIssue> find(
            GovernanceField field, GovernanceIssueStatus status, Long assetId) {
        var sql = new StringBuilder(issueSelect()).append(" WHERE 1 = 1");
        if (field != null) sql.append(" AND target_field = :field");
        if (status != null) sql.append(" AND status = :status");
        if (assetId != null) sql.append(" AND asset_id = :assetId");
        sql.append(" ORDER BY id");
        var statement = jdbcClient.sql(sql.toString());
        if (field != null) statement = statement.param("field", field.name());
        if (status != null) statement = statement.param("status", status.name());
        if (assetId != null) statement = statement.param("assetId", assetId);
        try {
            return statement.query((rs, ignored) -> mapIssue(rs)).list();
        } catch (DataAccessException exception) {
            throw storageFailure("治理问题查询失败");
        }
    }

    @Override
    public List<GovernanceIssue> findByIds(List<Long> issueIds) {
        if (issueIds == null || issueIds.isEmpty()) return List.of();
        try {
            var found = jdbcClient.sql(issueSelect() + " WHERE id IN (:ids)")
                    .param("ids", issueIds)
                    .query((rs, ignored) -> mapIssue(rs))
                    .list();
            var byId = new HashMap<Long, GovernanceIssue>();
            found.forEach(issue -> byId.put(issue.id(), issue));
            return issueIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        } catch (DataAccessException exception) {
            throw storageFailure("治理问题查询失败");
        }
    }

    @Override
    @Transactional
    public List<GovernanceIssue> insertAll(List<GovernanceIssue> issues) {
        requireWritable();
        if (issues == null || issues.isEmpty()) return List.of();
        var includeId = issues.stream().allMatch(issue -> issue.id() > 0);
        if (!includeId && issues.stream().anyMatch(issue -> issue.id() > 0)) {
            throw new IllegalArgumentException("治理问题 ID 必须全部指定或全部由数据库生成");
        }

        var columns = includeId ? "id, " + insertColumns() : insertColumns();
        var values = new StringBuilder();
        for (int index = 0; index < issues.size(); index++) {
            if (index > 0) values.append(", ");
            values.append(valueBindings(index, includeId));
        }
        var statement = jdbcClient.sql("INSERT INTO governance_issue (" + columns + ") VALUES " + values);
        for (int index = 0; index < issues.size(); index++) {
            var issue = issues.get(index);
            if (includeId) statement = statement.param("id" + index, issue.id());
            statement = statement
                    .param("assetId" + index, issue.assetId())
                    .param("targetField" + index, issue.targetField().name())
                    .param("issueType" + index, issue.issueType())
                    .param("targetPath" + index, issue.targetPath())
                    .param("ruleCode" + index, issue.ruleCode())
                    .param("ruleVersion" + index, issue.ruleVersion())
                    .param("originalFactJson" + index, issue.originalFactJson())
                    .param("assetVersion" + index, issue.assetVersion())
                    .param("scopeFingerprint" + index, issue.scopeFingerprint())
                    .param("severity" + index, issue.severity())
                    .param("blocking" + index, issue.blocking())
                    .param("status" + index, issue.status().name())
                    .param("taskId" + index, issue.taskId(), Types.BIGINT)
                    .param("fingerprint" + index, issue.fingerprint())
                    .param("version" + index, issue.version());
        }

        try {
            statement.update();
            return findByFingerprintsInRequestOrder(issues);
        } catch (DataIntegrityViolationException exception) {
            throw new GovernanceConflictException("治理问题已存在");
        } catch (DataAccessException exception) {
            throw storageFailure("治理问题保存失败");
        }
    }

    @Override
    @Transactional
    public void claimOpen(List<GovernanceIssue> expectedIssues, long taskId) {
        requireWritable();
        if (expectedIssues == null || expectedIssues.isEmpty()) {
            throw new IllegalArgumentException("问题 ID 不能为空");
        }
        var current = findByIds(expectedIssues.stream().map(GovernanceIssue::id).toList());
        var currentById = new HashMap<Long, GovernanceIssue>();
        current.forEach(issue -> currentById.put(issue.id(), issue));
        var valid = expectedIssues.size() == current.size() && expectedIssues.stream().allMatch(expected -> {
            var found = currentById.get(expected.id());
            return found != null && found.status() == GovernanceIssueStatus.OPEN
                    && found.version() == expected.version();
        });
        if (!valid) throw claimConflict();

        try {
            for (var expected : expectedIssues) {
                var updated = jdbcClient.sql("""
                        UPDATE governance_issue
                        SET status = 'CLAIMED', task_id = :taskId, version = version + 1
                        WHERE id = :id AND status = 'OPEN' AND version = :expectedVersion
                        """)
                        .param("taskId", taskId)
                        .param("id", expected.id())
                        .param("expectedVersion", expected.version())
                        .update();
                if (updated != 1) throw claimConflict();
            }
        } catch (GovernanceConflictException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw storageFailure("治理问题领取失败");
        }
    }

    @Override
    public List<GovernanceIssue> findClaimedByTask(long taskId) {
        try {
            return jdbcClient.sql(issueSelect() + " WHERE task_id = :taskId AND status = 'CLAIMED' ORDER BY id")
                    .param("taskId", taskId)
                    .query((rs, ignored) -> mapIssue(rs))
                    .list();
        } catch (DataAccessException exception) {
            throw storageFailure("治理问题查询失败");
        }
    }

    @Override
    @Transactional
    public GovernanceIssue resolve(long issueId, long expectedVersion) {
        requireWritable();
        var current = findByIds(List.of(issueId)).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("治理问题不存在"));
        if (current.status() == GovernanceIssueStatus.RESOLVED) return current;
        var updated = jdbcClient.sql("""
                UPDATE governance_issue
                SET status = 'RESOLVED', version = version + 1
                WHERE id = :id AND status = 'CLAIMED' AND version = :expectedVersion
                """)
                .param("id", issueId)
                .param("expectedVersion", expectedVersion)
                .update();
        if (updated != 1) throw new GovernanceConflictException("治理问题已变化，请刷新后重试");
        return findByIds(List.of(issueId)).getFirst();
    }

    private List<GovernanceIssue> findByFingerprintsInRequestOrder(List<GovernanceIssue> requested) {
        var fingerprints = requested.stream().map(GovernanceIssue::fingerprint).toList();
        var found = jdbcClient.sql(issueSelect() + " WHERE fingerprint IN (:fingerprints)")
                .param("fingerprints", fingerprints)
                .query((rs, ignored) -> mapIssue(rs))
                .list();
        Map<String, GovernanceIssue> byFingerprint = new HashMap<>();
        found.forEach(issue -> byFingerprint.put(issue.fingerprint(), issue));
        return fingerprints.stream().map(byFingerprint::get).toList();
    }

    private GovernanceIssue mapIssue(ResultSet rs) throws SQLException {
        try {
            return new GovernanceIssue(
                    rs.getLong("id"), rs.getLong("asset_id"),
                    GovernanceField.valueOf(rs.getString("target_field")), rs.getString("issue_type"),
                    rs.getString("target_path"), rs.getString("rule_code"), rs.getLong("rule_version"),
                    rs.getString("original_fact_json"), rs.getLong("asset_version"),
                    rs.getString("scope_fingerprint"), rs.getString("severity"), rs.getBoolean("blocking"),
                    GovernanceIssueStatus.valueOf(rs.getString("status")), nullableLong(rs, "task_id"),
                    rs.getLong("version"));
        } catch (IllegalArgumentException exception) {
            throw storageFailure("治理问题数据损坏");
        }
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        var value = rs.getObject(column, Long.class);
        return rs.wasNull() ? null : value;
    }

    private String issueSelect() {
        return """
                SELECT id, asset_id, target_field, issue_type, target_path, rule_code, rule_version,
                       original_fact_json, asset_version, scope_fingerprint, severity, blocking,
                       status, task_id, version
                FROM governance_issue
                """;
    }

    private String insertColumns() {
        return """
                asset_id, target_field, issue_type, target_path, rule_code, rule_version,
                original_fact_json, asset_version, scope_fingerprint, severity, blocking,
                status, task_id, fingerprint, version
                """;
    }

    private String valueBindings(int index, boolean includeId) {
        var id = includeId ? ":id" + index + ", " : "";
        return "(" + id
                + ":assetId" + index + ", :targetField" + index + ", :issueType" + index
                + ", :targetPath" + index + ", :ruleCode" + index + ", :ruleVersion" + index
                + ", :originalFactJson" + index + ", :assetVersion" + index + ", :scopeFingerprint" + index
                + ", :severity" + index + ", :blocking" + index + ", :status" + index
                + ", :taskId" + index + ", :fingerprint" + index + ", :version" + index + ")";
    }

    private GovernanceConflictException claimConflict() {
        return new GovernanceConflictException("问题已被其他治理任务纳入");
    }

    private GovernanceStorageException storageFailure(String message) {
        return new GovernanceStorageException(message);
    }

    private void requireWritable() {
        if (!databaseWritesEnabled) {
            throw new GovernanceTaskStateException("当前数据库配置为只读，不能修改治理问题");
        }
    }
}
