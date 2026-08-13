package com.tianshu.assets.governance.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.application.GovernanceTaskStateException;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import com.tianshu.assets.governance.issue.domain.GovernanceIssue;
import com.tianshu.assets.governance.issue.domain.GovernanceIssueStatus;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class JdbcGovernanceIssueStoreTest {

    private JdbcGovernanceIssueStore store;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("governance-issue;MODE=MySQL;DB_CLOSE_DELAY=-1")
                .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP ALL OBJECTS");
        jdbcTemplate.execute("""
                CREATE TABLE governance_issue (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    asset_id BIGINT NOT NULL,
                    target_field VARCHAR(40) NOT NULL,
                    issue_type VARCHAR(80) NOT NULL,
                    target_path VARCHAR(300) NOT NULL,
                    rule_code VARCHAR(100) NOT NULL,
                    rule_version BIGINT NOT NULL,
                    original_fact_json VARCHAR(2000),
                    asset_version BIGINT NOT NULL,
                    scope_fingerprint VARCHAR(500),
                    severity VARCHAR(20) NOT NULL,
                    blocking BOOLEAN NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    task_id BIGINT,
                    fingerprint VARCHAR(800) NOT NULL UNIQUE,
                    version BIGINT NOT NULL,
                    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);
        store = new JdbcGovernanceIssueStore(JdbcClient.create(dataSource), true);
    }

    @Test
    void insertsAndFiltersIssuesWithBoundValues() {
        var inserted = store.insertAll(List.of(
                issue(101, GovernanceField.DESCRIPTION, "MISSING_DESCRIPTION", "/description"),
                issue(102, GovernanceField.OWNER, "MISSING_OWNER", "/ownerUserId")));

        assertThat(inserted).hasSize(2).allMatch(issue -> issue.id() > 0);
        assertThat(store.find(GovernanceField.DESCRIPTION, GovernanceIssueStatus.OPEN, 101L))
                .extracting(GovernanceIssue::targetPath)
                .containsExactly("/description");
    }

    @Test
    void rejectsDuplicateFingerprintWithoutPartialInsertion() {
        var duplicate = issue(101, GovernanceField.DESCRIPTION, "MISSING_DESCRIPTION", "/description");

        assertThatThrownBy(() -> store.insertAll(List.of(duplicate, duplicate)))
                .isInstanceOf(GovernanceConflictException.class)
                .hasMessage("治理问题已存在")
                .hasNoCause();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM governance_issue", Integer.class)).isZero();
    }

    @Test
    void rejectsBatchClaimBeforeChangingAnyIssueWhenOneIsNotOpen() {
        var inserted = store.insertAll(List.of(
                issue(101, GovernanceField.DESCRIPTION, "MISSING_DESCRIPTION", "/description"),
                issue(102, GovernanceField.OWNER, "MISSING_OWNER", "/ownerUserId")));
        jdbcTemplate.update("UPDATE governance_issue SET status = 'CLAIMED', task_id = 88, version = 1 WHERE id = ?",
                inserted.get(1).id());

        assertThatThrownBy(() -> store.claimOpen(inserted, 99))
                .isInstanceOf(GovernanceConflictException.class)
                .hasMessage("问题已被其他治理任务纳入");
        assertThat(store.findByIds(List.of(inserted.getFirst().id())).getFirst().status())
                .isEqualTo(GovernanceIssueStatus.OPEN);
        assertThat(store.findClaimedByTask(99)).isEmpty();
    }

    @Test
    void claimsEveryIssueAndIncrementsVersions() {
        var inserted = store.insertAll(List.of(
                issue(101, GovernanceField.DESCRIPTION, "MISSING_DESCRIPTION", "/description"),
                issue(102, GovernanceField.OWNER, "MISSING_OWNER", "/ownerUserId")));

        store.claimOpen(inserted, 99);

        assertThat(store.findClaimedByTask(99))
                .hasSize(2)
                .allMatch(issue -> issue.status() == GovernanceIssueStatus.CLAIMED
                        && issue.taskId() == 99 && issue.version() == 1);
    }

    @Test
    void rejectsWritesWhenDatabaseWritesAreDisabled() {
        var readOnlyStore = new JdbcGovernanceIssueStore(
                JdbcClient.create(jdbcTemplate.getDataSource()), false);

        assertThatThrownBy(() -> readOnlyStore.insertAll(List.of(
                        issue(101, GovernanceField.DESCRIPTION, "MISSING_DESCRIPTION", "/description"))))
                .isInstanceOf(GovernanceTaskStateException.class)
                .hasMessage("当前数据库配置为只读，不能修改治理问题");
    }

    private GovernanceIssue issue(
            long assetId, GovernanceField field, String issueType, String targetPath) {
        return new GovernanceIssue(
                0, assetId, field, issueType, targetPath, "FIELD_REQUIRED", 1,
                "", 0, "", "HIGH", true, GovernanceIssueStatus.OPEN, null, 0,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"));
    }
}
