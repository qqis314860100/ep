package com.tianshu.assets.governance.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.governance.application.GovernanceTaskStateException;
import com.tianshu.assets.governance.task.application.GovernanceStorageException;
import com.tianshu.assets.governance.task.application.GovernanceTaskStore;
import com.tianshu.assets.governance.task.domain.GovernancePlan;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import com.tianshu.assets.governance.task.domain.GovernanceWorkflowVersion;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

class JdbcGovernanceTaskStoreTest {

    private JdbcGovernanceTaskStore store;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("governance-task;MODE=MySQL;DB_CLOSE_DELAY=-1")
                .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP ALL OBJECTS");
        jdbcTemplate.execute("""
                CREATE TABLE governance_task (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    task_number VARCHAR(64) NOT NULL UNIQUE,
                    name VARCHAR(300) NOT NULL,
                    status VARCHAR(40) NOT NULL,
                    scope_description VARCHAR(1000),
                    issue_type VARCHAR(80),
                    owner_user_id VARCHAR(100) NOT NULL,
                    owner_name VARCHAR(100) NOT NULL,
                    assignee_id VARCHAR(100),
                    due_date DATE,
                    target_quantity BIGINT NOT NULL DEFAULT 0,
                    completed_quantity BIGINT NOT NULL DEFAULT 0,
                    workflow_version VARCHAR(40) NOT NULL,
                    current_round INT NOT NULL DEFAULT 0,
                    scope_snapshot_id BIGINT,
                    quality_policy_snapshot_id BIGINT,
                    version BIGINT NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE governance_plan (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    task_id BIGINT NOT NULL,
                    sequence_number INT NOT NULL,
                    name VARCHAR(300) NOT NULL,
                    responsible_user_id VARCHAR(100),
                    start_date DATE,
                    due_date DATE,
                    target_quantity BIGINT NOT NULL,
                    completed_quantity BIGINT NOT NULL,
                    quantity_unit VARCHAR(40) NOT NULL,
                    status VARCHAR(40) NOT NULL,
                    completed_at DATE,
                    actual_start DATE,
                    actual_end DATE,
                    dependency_ids VARCHAR(1000),
                    version BIGINT NOT NULL DEFAULT 0,
                    UNIQUE (task_id, sequence_number)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE governance_plan_item (
                    plan_id BIGINT NOT NULL,
                    issue_id BIGINT NOT NULL,
                    PRIMARY KEY (plan_id, issue_id)
                )
                """);
        store = new JdbcGovernanceTaskStore(
                JdbcClient.create(dataSource), new ObjectMapper(), true,
                new DataSourceTransactionManager(dataSource));
    }

    @Test
    void roundTripsActionAndIssueTypeFields() {
        var inserted = store.insert(new GovernanceTask(
                0, "GOV-NEW-001", "历史字段补充", "NORMALIZE", "MISSING_DESCRIPTION",
                "emp-chen", "陈工", "emp-li", LocalDate.of(2026, 9, 1),
                GovernanceTaskStatus.DRAFT, 0, GovernanceWorkflowVersion.CLOSED_LOOP_V1, null, null, 0, 0, 0));

        assertThat(inserted.actionType()).isEqualTo("NORMALIZE");
        assertThat(inserted.issueType()).isEqualTo("MISSING_DESCRIPTION");
    }

    @Test
    void reloadsEveryPersistedTaskField() {
        var requested = new GovernanceTask(
                0, "GOV-LEGACY-NULL-DATE", "未排期历史任务", "MANUAL_PROGRESS", "LEGACY_IMPORT",
                "emp-wang", "王工", "emp-li", null, GovernanceTaskStatus.IN_PROGRESS, 2,
                GovernanceWorkflowVersion.LEGACY_PROGRESS, 501L, 601L, 12, 7, 0);

        var inserted = store.insert(requested);
        var reloaded = store.findById(inserted.id()).orElseThrow();

        assertThat(reloaded).isEqualTo(new GovernanceTask(
                inserted.id(), requested.taskNumber(), requested.name(), requested.actionType(),
                requested.issueType(), requested.ownerUserId(), requested.ownerName(), requested.assigneeId(),
                requested.dueDate(), requested.status(), requested.currentRound(), requested.workflowVersion(),
                requested.scopeSnapshotId(), requested.qualityPolicySnapshotId(), requested.legacyTotal(),
                requested.legacyCompleted(), 0));
    }

    @Test
    void inMemoryUpdateChangesOnlyMutableFields() {
        assertUpdateChangesOnlyMutableFields(new InMemoryGovernanceTaskStore());
    }

    @Test
    void jdbcUpdateChangesOnlyMutableFields() {
        assertUpdateChangesOnlyMutableFields(store);
    }

    @Test
    void insertsPlansWithGeneratedIdsAndSequentialPositions() {
        var task = insertClosedLoopTask("GOV-PLAN-001");

        var first = store.insertPlan(planFor(task.id(), "导出问题清单"));
        var second = store.insertPlan(planFor(task.id(), "补充缺失字段"));

        assertThat(first.id()).isPositive();
        assertThat(first.sequence()).isEqualTo(1);
        assertThat(first.issueIds()).containsExactly(1001L, 1002L);
        assertThat(jdbcTemplate.queryForList(
                "SELECT issue_id FROM governance_plan_item WHERE plan_id = ? ORDER BY issue_id",
                Long.class, first.id())).containsExactly(1001L, 1002L);
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(jdbcTemplate.queryForList(
                "SELECT sequence_number FROM governance_plan ORDER BY id", Integer.class))
                .containsExactly(1, 2);
    }

    @Test
    void readsLegacyPlanWithoutJoinRowsAsEmptyIssueList() {
        var task = insertClosedLoopTask("GOV-PLAN-LEGACY");
        jdbcTemplate.update("""
                INSERT INTO governance_plan
                    (task_id, sequence_number, name, responsible_user_id, start_date, due_date,
                     target_quantity, completed_quantity, quantity_unit, status, dependency_ids, version)
                VALUES (?, 1, '旧计划', 'emp-chen', '2026-08-20', '2026-08-21',
                        2, 0, '个字段', 'TODO', '[]', 0)
                """, task.id());

        assertThat(store.findPlans(task.id())).singleElement()
                .extracting(GovernancePlan::issueIds)
                .isEqualTo(List.of());
    }

    @Test
    void rollsBackPlanWhenJoinInsertionFails() {
        var task = insertClosedLoopTask("GOV-PLAN-ROLLBACK");
        jdbcTemplate.execute("""
                ALTER TABLE governance_plan_item
                ADD CONSTRAINT reject_issue_id CHECK (issue_id > 9999)
                """);

        assertThatThrownBy(() -> store.insertPlan(planFor(task.id(), "关联写入失败")))
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM governance_plan WHERE task_id = ?", Integer.class, task.id()))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM governance_plan_item", Integer.class))
                .isZero();
    }

    @Test
    void translatesPlanSequenceIntegrityConflict() {
        var task = insertClosedLoopTask("GOV-PLAN-002");
        jdbcTemplate.execute("""
                ALTER TABLE governance_plan ADD CONSTRAINT force_sequence_conflict CHECK (sequence_number > 100)
                """);

        assertThatThrownBy(() -> store.insertPlan(planFor(task.id(), "导出问题清单")))
                .isInstanceOf(GovernanceTaskStateException.class)
                .hasMessage("治理计划序号冲突，请刷新后重试");
    }

    @Test
    void reportsCorruptDependencyJsonAsStorageFailureWithoutRawData() {
        var task = insertClosedLoopTask("GOV-PLAN-003");
        jdbcTemplate.update("""
                INSERT INTO governance_plan
                    (id, task_id, sequence_number, name, responsible_user_id, start_date, due_date,
                     target_quantity, completed_quantity, quantity_unit, status, dependency_ids, version)
                VALUES (900, ?, 1, '损坏依赖', 'emp-chen', '2026-08-20', '2026-08-21',
                        1, 0, '个字段', 'TODO', '{sensitive malformed json', 0)
                """, task.id());

        assertThatThrownBy(() -> store.findPlans(task.id()))
                .isInstanceOf(GovernanceStorageException.class)
                .hasMessage("治理计划 900 的依赖数据损坏")
                .hasNoCause()
                .message().doesNotContain("sensitive malformed json");
    }

    private void assertUpdateChangesOnlyMutableFields(GovernanceTaskStore taskStore) {
        var inserted = taskStore.insert(new GovernanceTask(
                0, "GOV-NEW-002", "历史字段补充", "NORMALIZE", "MISSING_DESCRIPTION",
                "emp-chen", "陈工", "emp-chen", LocalDate.of(2026, 9, 1),
                GovernanceTaskStatus.DRAFT, 0, GovernanceWorkflowVersion.CLOSED_LOOP_V1,
                null, null, 0, 0, 0));
        var requested = new GovernanceTask(
                inserted.id(), "CHANGED-NUMBER", "不应更改的名称", "SPLIT", "DUPLICATE",
                "emp-wang", "王工", "emp-li", LocalDate.of(2026, 9, 15),
                GovernanceTaskStatus.IN_PROGRESS, 1, GovernanceWorkflowVersion.CLOSED_LOOP_V1,
                88L, 99L, 12, 6, inserted.version());

        var updated = taskStore.update(requested, inserted.version());

        assertThat(updated.taskNumber()).isEqualTo(inserted.taskNumber());
        assertThat(updated.name()).isEqualTo(inserted.name());
        assertThat(updated.actionType()).isEqualTo(inserted.actionType());
        assertThat(updated.issueType()).isEqualTo(inserted.issueType());
        assertThat(updated.ownerUserId()).isEqualTo(inserted.ownerUserId());
        assertThat(updated.ownerName()).isEqualTo(inserted.ownerName());
        assertThat(updated.workflowVersion()).isEqualTo(inserted.workflowVersion());
        assertThat(updated.legacyTotal()).isEqualTo(inserted.legacyTotal());
        assertThat(updated.legacyCompleted()).isEqualTo(inserted.legacyCompleted());
        assertThat(updated.assigneeId()).isEqualTo("emp-li");
        assertThat(updated.dueDate()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(updated.status()).isEqualTo(GovernanceTaskStatus.IN_PROGRESS);
        assertThat(updated.currentRound()).isEqualTo(1);
        assertThat(updated.scopeSnapshotId()).isEqualTo(88L);
        assertThat(updated.qualityPolicySnapshotId()).isEqualTo(99L);
        assertThat(updated.version()).isEqualTo(1);
    }

    private GovernanceTask insertClosedLoopTask(String taskNumber) {
        return store.insert(new GovernanceTask(
                0, taskNumber, "历史字段补充", "NORMALIZE", "MISSING_DESCRIPTION",
                "emp-chen", "陈工", "emp-chen", LocalDate.of(2026, 9, 1),
                GovernanceTaskStatus.DRAFT, 0, GovernanceWorkflowVersion.CLOSED_LOOP_V1,
                null, null, 0, 0, 0));
    }

    private GovernancePlan planFor(long taskId, String title) {
        return GovernancePlan.closedLoop(
                0, taskId, 0, title, "emp-chen", LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 21), List.of(), List.of(1002L, 1001L), 0);
    }
}
