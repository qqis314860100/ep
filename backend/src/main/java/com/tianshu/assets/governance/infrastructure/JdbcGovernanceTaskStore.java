package com.tianshu.assets.governance.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.governance.application.GovernanceTaskStateException;
import com.tianshu.assets.governance.domain.GovernanceEmployee;
import com.tianshu.assets.governance.task.application.GovernanceEmployeeDirectory;
import com.tianshu.assets.governance.task.application.GovernanceStorageException;
import com.tianshu.assets.governance.task.application.GovernanceTaskStore;
import com.tianshu.assets.governance.task.domain.GovernancePlan;
import com.tianshu.assets.governance.task.domain.GovernancePlanStatus;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import com.tianshu.assets.governance.task.domain.GovernanceWorkflowVersion;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
@Profile({"local", "oceanbase"})
@ConditionalOnProperty(name = "asset.governance-schema-enabled", havingValue = "true")
public class JdbcGovernanceTaskStore implements GovernanceTaskStore, GovernanceEmployeeDirectory {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final boolean databaseWritesEnabled;
    private final TransactionTemplate transactions;

    public JdbcGovernanceTaskStore(
            JdbcClient jdbcClient,
            ObjectMapper objectMapper,
            @Value("${asset.database-writes-enabled:false}") boolean databaseWritesEnabled,
            PlatformTransactionManager transactionManager) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        this.databaseWritesEnabled = databaseWritesEnabled;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public List<GovernanceTask> findAll() {
        return jdbcClient.sql(taskSelect() + " ORDER BY id")
                .query((rs, ignored) -> mapTask(rs))
                .list();
    }

    @Override
    public Optional<GovernanceTask> findById(long taskId) {
        return jdbcClient.sql(taskSelect() + " WHERE id = :id")
                .param("id", taskId)
                .query((rs, ignored) -> mapTask(rs))
                .optional();
    }

    @Override
    public GovernanceTask insert(GovernanceTask task) {
        requireWritable();
        jdbcClient.sql("""
                INSERT INTO governance_task
                    (task_number, name, status, scope_description, issue_type, owner_user_id, owner_name, assignee_id,
                     due_date, target_quantity, completed_quantity, workflow_version, current_round,
                     scope_snapshot_id, quality_policy_snapshot_id, version)
                VALUES (:taskNumber, :name, :status, :actionType, :issueType, :ownerUserId, :ownerName,
                        :assigneeId, :dueDate, :legacyTotal, :legacyCompleted, :workflowVersion,
                        :currentRound, :scopeSnapshotId, :qualityPolicySnapshotId, 0)
                """)
                .param("taskNumber", task.taskNumber())
                .param("name", task.name())
                .param("status", task.status().name())
                .param("actionType", task.actionType())
                .param("issueType", task.issueType())
                .param("ownerUserId", task.ownerUserId())
                .param("ownerName", task.ownerName())
                .param("assigneeId", task.assigneeId())
                .param("dueDate", task.dueDate())
                .param("legacyTotal", task.legacyTotal())
                .param("legacyCompleted", task.legacyCompleted())
                .param("workflowVersion", task.workflowVersion().name())
                .param("currentRound", task.currentRound())
                .param("scopeSnapshotId", task.scopeSnapshotId())
                .param("qualityPolicySnapshotId", task.qualityPolicySnapshotId())
                .update();
        return findByTaskNumber(task.taskNumber());
    }

    @Override
    public GovernanceTask update(GovernanceTask task, long expectedVersion) {
        requireWritable();
        var current = findById(task.id())
                .orElseThrow(() -> new IllegalArgumentException("治理任务不存在"));
        if (current.workflowVersion() == GovernanceWorkflowVersion.LEGACY_PROGRESS) {
            throw new GovernanceTaskStateException(GovernanceTaskStateException.LEGACY_READ_ONLY_MESSAGE);
        }
        var normalized = current.applyMutableState(task, expectedVersion + 1);
        var updated = jdbcClient.sql("""
                UPDATE governance_task
                SET status = :status, assignee_id = :assigneeId, due_date = :dueDate,
                    current_round = :currentRound, scope_snapshot_id = :scopeSnapshotId,
                    quality_policy_snapshot_id = :qualityPolicySnapshotId, version = version + 1
                WHERE id = :id AND version = :expectedVersion
                """)
                .param("status", normalized.status().name())
                .param("assigneeId", normalized.assigneeId())
                .param("dueDate", normalized.dueDate())
                .param("currentRound", normalized.currentRound())
                .param("scopeSnapshotId", normalized.scopeSnapshotId())
                .param("qualityPolicySnapshotId", normalized.qualityPolicySnapshotId())
                .param("id", normalized.id())
                .param("expectedVersion", expectedVersion)
                .update();
        if (updated != 1) {
            throw new GovernanceTaskStateException("治理任务已被其他用户更新，请刷新后重试");
        }
        return findById(task.id()).orElseThrow();
    }

    @Override
    public List<GovernancePlan> findPlans(long taskId) {
        var plans = jdbcClient.sql("""
                SELECT id, task_id, sequence_number, name, status, completed_at, start_date, due_date, actual_start,
                       actual_end, target_quantity, completed_quantity, quantity_unit,
                       responsible_user_id, dependency_ids, version
                FROM governance_plan WHERE task_id = :taskId ORDER BY sequence_number, id
                """)
                .param("taskId", taskId)
                .query((rs, ignored) -> mapPlan(rs))
                .list();
        if (plans.isEmpty()) return plans;
        var issueIdsByPlan = findPlanIssueIds(taskId);
        return plans.stream().map(plan -> withIssueIds(
                plan, issueIdsByPlan.getOrDefault(plan.id(), List.of()))).toList();
    }

    @Override
    public GovernancePlan insertPlan(GovernancePlan plan) {
        var inserted = transactions.execute(status -> insertPlanInTransaction(plan));
        if (inserted == null) throw new IllegalStateException("新增治理计划事务未完成");
        return inserted;
    }

    private GovernancePlan insertPlanInTransaction(GovernancePlan plan) {
        requireWritable();
        var task = findById(plan.taskId())
                .orElseThrow(() -> new IllegalArgumentException("治理任务不存在"));
        if (task.workflowVersion() == GovernanceWorkflowVersion.LEGACY_PROGRESS) {
            throw new GovernanceTaskStateException(GovernanceTaskStateException.LEGACY_READ_ONLY_MESSAGE);
        }
        if (task.status() != GovernanceTaskStatus.DRAFT) {
            throw new GovernanceTaskStateException("治理任务启动后计划已锁定");
        }
        var keyHolder = new GeneratedKeyHolder();
        try {
            jdbcClient.sql("""
                    INSERT INTO governance_plan
                        (task_id, sequence_number, name, responsible_user_id, start_date, due_date,
                         target_quantity, completed_quantity, quantity_unit, status, completed_at,
                         actual_start, actual_end, dependency_ids, version)
                    SELECT :taskId, COALESCE(MAX(sequence_number), 0) + 1, :name, :assigneeId,
                           :startDate, :dueDate, :plannedQuantity, :completedQuantity, :quantityUnit,
                           :status, :completedAt, :actualStart, :actualEnd, :dependencyIds, 0
                    FROM governance_plan WHERE task_id = :taskId
                    """)
                    .param("taskId", plan.taskId())
                    .param("name", plan.title())
                    .param("assigneeId", plan.assigneeId())
                    .param("startDate", plan.plannedStart())
                    .param("dueDate", plan.plannedEnd())
                    .param("plannedQuantity", plan.plannedQuantity())
                    .param("completedQuantity", plan.completedQuantity())
                    .param("quantityUnit", plan.quantityUnit())
                    .param("status", plan.status())
                    .param("completedAt", plan.completedAt())
                    .param("actualStart", plan.actualStart())
                    .param("actualEnd", plan.actualEnd())
                    .param("dependencyIds", writeLongs(plan.dependencyIds()))
                    .update(keyHolder, "id");
        } catch (DataIntegrityViolationException exception) {
            throw new GovernanceTaskStateException("治理计划序号冲突，请刷新后重试");
        }
        var id = keyHolder.getKeyAs(Long.class);
        if (id == null) throw new IllegalStateException("新增治理计划未返回 ID");
        insertPlanItems(id, plan.issueIds());
        return findPlans(plan.taskId()).stream().filter(item -> item.id() == id).findFirst().orElseThrow();
    }

    private void insertPlanItems(long planId, List<Long> issueIds) {
        if (issueIds == null || issueIds.isEmpty()) return;
        var values = new StringBuilder();
        for (int index = 0; index < issueIds.size(); index++) {
            if (index > 0) values.append(", ");
            values.append("(:planId, :issueId").append(index).append(')');
        }
        var statement = jdbcClient.sql(
                "INSERT INTO governance_plan_item (plan_id, issue_id) VALUES " + values)
                .param("planId", planId);
        for (int index = 0; index < issueIds.size(); index++) {
            statement = statement.param("issueId" + index, issueIds.get(index));
        }
        statement.update();
    }

    private Map<Long, List<Long>> findPlanIssueIds(long taskId) {
        var grouped = new LinkedHashMap<Long, List<Long>>();
        var rows = jdbcClient.sql("""
                SELECT item.plan_id, item.issue_id
                FROM governance_plan_item item
                JOIN governance_plan plan ON plan.id = item.plan_id
                WHERE plan.task_id = :taskId
                ORDER BY plan.sequence_number, plan.id, item.issue_id
                """)
                .param("taskId", taskId)
                .query((rs, ignored) -> new PlanIssueRow(
                        rs.getLong("plan_id"), rs.getLong("issue_id")))
                .list();
        for (var row : rows) {
            grouped.computeIfAbsent(row.planId(), ignored -> new java.util.ArrayList<>()).add(row.issueId());
        }
        return grouped;
    }

    @Override
    public List<GovernanceEmployee> findAllEmployees() {
        return jdbcClient.sql("""
                SELECT code, name FROM temp_person WHERE status = 1 AND code LIKE 'emp-%' ORDER BY id
                """)
                .query((rs, ignored) -> new GovernanceEmployee(
                        rs.getString("code"), rs.getString("name"), "本地员工目录", "OFFICE_DIRECTORY"))
                .list();
    }

    private GovernanceTask findByTaskNumber(String taskNumber) {
        return jdbcClient.sql(taskSelect() + " WHERE task_number = :taskNumber")
                .param("taskNumber", taskNumber)
                .query((rs, ignored) -> mapTask(rs))
                .single();
    }

    private String taskSelect() {
        return """
                SELECT id, task_number, name, scope_description AS action_type,
                       issue_type, owner_user_id, owner_name, assignee_id,
                       due_date, status, current_round, workflow_version, scope_snapshot_id,
                       quality_policy_snapshot_id, target_quantity, completed_quantity, version
                FROM governance_task
                """;
    }

    private GovernanceTask mapTask(ResultSet rs) throws SQLException {
        return new GovernanceTask(
                rs.getLong("id"), rs.getString("task_number"), rs.getString("name"),
                rs.getString("action_type"), rs.getString("issue_type"), rs.getString("owner_user_id"),
                rs.getString("owner_name"), rs.getString("assignee_id"), toLocalDate(rs, "due_date"),
                GovernanceTaskStatus.valueOf(rs.getString("status")), rs.getInt("current_round"),
                GovernanceWorkflowVersion.valueOf(rs.getString("workflow_version")),
                nullableLong(rs, "scope_snapshot_id"), nullableLong(rs, "quality_policy_snapshot_id"),
                rs.getInt("target_quantity"), rs.getInt("completed_quantity"), rs.getLong("version"));
    }

    private GovernancePlan mapPlan(ResultSet rs) throws SQLException {
        var planId = rs.getLong("id");
        var taskId = rs.getLong("task_id");
        var status = rs.getString("status");
        return new GovernancePlan(
                planId, taskId, rs.getInt("sequence_number"), rs.getString("name"), planStatus(status), status,
                toLocalDate(rs, "completed_at"), toLocalDate(rs, "start_date"), toLocalDate(rs, "due_date"),
                toLocalDate(rs, "actual_start"), toLocalDate(rs, "actual_end"),
                rs.getInt("target_quantity"), rs.getInt("completed_quantity"), rs.getString("quantity_unit"),
                rs.getString("responsible_user_id"), rs.getString("responsible_user_id"),
                readLongs(rs.getString("dependency_ids"), planId), List.of(),
                rs.getLong("version"));
    }

    private GovernancePlan withIssueIds(GovernancePlan plan, List<Long> issueIds) {
        return new GovernancePlan(
                plan.id(), plan.taskId(), plan.sequence(), plan.title(), plan.planStatus(), plan.status(),
                plan.completedAt(), plan.plannedStart(), plan.plannedEnd(), plan.actualStart(), plan.actualEnd(),
                plan.plannedQuantity(), plan.completedQuantity(), plan.quantityUnit(), plan.assigneeId(),
                plan.responsibleUserId(), plan.dependencyIds(), issueIds, plan.version());
    }

    private GovernancePlanStatus planStatus(String status) {
        if (status == null || status.isBlank() || "TODO".equals(status)) return GovernancePlanStatus.NOT_STARTED;
        try {
            return GovernancePlanStatus.valueOf(status);
        } catch (IllegalArgumentException ignored) {
            return GovernancePlanStatus.NOT_STARTED;
        }
    }

    private LocalDate toLocalDate(ResultSet rs, String column) throws SQLException {
        var value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        var value = rs.getObject(column, Long.class);
        return rs.wasNull() ? null : value;
    }

    private List<Long> readLongs(String json, long planId) {
        try {
            return json == null || json.isBlank() ? List.of() : objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new GovernanceStorageException("治理计划 " + planId + " 的依赖数据损坏");
        }
    }

    private String writeLongs(List<Long> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception exception) {
            throw new IllegalArgumentException("计划依赖无法保存", exception);
        }
    }

    private void requireWritable() {
        if (!databaseWritesEnabled) {
            throw new GovernanceTaskStateException("当前数据库配置为只读，不能修改治理任务");
        }
    }

    private record PlanIssueRow(long planId, long issueId) {}
}
