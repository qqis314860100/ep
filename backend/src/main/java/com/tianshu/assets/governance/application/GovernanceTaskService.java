package com.tianshu.assets.governance.application;

import com.tianshu.assets.governance.domain.GovernanceTask;
import com.tianshu.assets.governance.domain.GovernanceEmployee;
import com.tianshu.assets.governance.domain.GovernancePlan;
import com.tianshu.assets.governance.domain.GovernanceTaskStatus;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GovernanceTaskService {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final boolean databaseWritesEnabled;

    public GovernanceTaskService() {
        this.jdbcClient = null;
        this.objectMapper = null;
        this.databaseWritesEnabled = false;
    }

    @Autowired
    public GovernanceTaskService(ObjectProvider<JdbcClient> jdbcClientProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            @Value("${asset.database-writes-enabled:false}") boolean databaseWritesEnabled) {
        this.jdbcClient = jdbcClientProvider.getIfAvailable();
        this.objectMapper = objectMapperProvider.getIfAvailable();
        this.databaseWritesEnabled = databaseWritesEnabled && this.jdbcClient != null;
    }

    private final AtomicLong nextId = new AtomicLong(4);
    private final AtomicLong nextPlanId = new AtomicLong(401);
    private final List<GovernanceTask> tasks = new CopyOnWriteArrayList<>(List.of(
            new GovernanceTask(1, "A 拉线历史数模范围补充", "旧拉线：XM-PL01、A线", "陈工", 286, 174,
                    LocalDate.of(2026, 8, 15), GovernanceTaskStatus.IN_PROGRESS),
            new GovernanceTask(2, "历史专业类别标准化", "机械、电气自由文本", "李工", 421, 421,
                    LocalDate.of(2026, 7, 31), GovernanceTaskStatus.PENDING_CONFIRMATION),
            new GovernanceTask(3, "失效文件引用治理", "无法访问的对象存储文件", "王工", 37, 37,
                    LocalDate.of(2026, 7, 25), GovernanceTaskStatus.COMPLETED)));

    private final List<GovernanceEmployee> employees = List.of(
            new GovernanceEmployee("emp-chen", "陈工", "制造工程部", "OFFICE_DIRECTORY"),
            new GovernanceEmployee("emp-li", "李工", "标准化小组", "OFFICE_DIRECTORY"),
            new GovernanceEmployee("emp-wang", "王工", "资料管理组", "OFFICE_DIRECTORY"));

    private final Map<Long, List<GovernancePlan>> plans = new ConcurrentHashMap<>(Map.of(
            1L, Arrays.asList(
                    new GovernancePlan(101, 1, "导出历史模组资产清单", "DONE", LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), 286, 286, "个资产", "emp-chen", List.of()),
                    new GovernancePlan(102, 1, "补充平台、基地和拉线范围", "IN_PROGRESS", null, LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 9), LocalDate.of(2026, 8, 3), null, 286, 174, "个资产", "emp-chen", List.of(101L)),
                    new GovernancePlan(103, 1, "提交业务专家确认", "TODO", null, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12), null, null, 174, 0, "个资产", "emp-li", List.of(102L))),
            2L, List.of(
                    new GovernancePlan(201, 2, "整理历史专业自由文本", "DONE", LocalDate.of(2026, 7, 12), LocalDate.of(2026, 7, 8), LocalDate.of(2026, 7, 12), LocalDate.of(2026, 7, 8), LocalDate.of(2026, 7, 12), 421, 421, "个字段", "emp-li", List.of()),
                    new GovernancePlan(202, 2, "提交标准化结果确认", "DONE", LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 15), 421, 421, "个字段", "emp-li", List.of(201L))),
            3L, List.of(
                    new GovernancePlan(301, 3, "检查对象存储文件可访问性", "DONE", LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 16), LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 16), LocalDate.of(2026, 7, 17), 37, 37, "个文件", "emp-wang", List.of()),
                    new GovernancePlan(302, 3, "登记失效引用处理结论", "DONE", LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 18), 37, 37, "个文件", "emp-wang", List.of(301L)))));
    private final Map<Long, String> assignments = new ConcurrentHashMap<>(Map.of(
            1L, "emp-chen", 2L, "emp-li", 3L, "emp-wang"));

    public List<GovernanceTask> list() {
        if (databaseWritesEnabled) {
            return jdbcClient.sql("""
                    SELECT id, name, scope_description, owner_name, target_quantity, completed_quantity,
                           due_date, status FROM governance_task ORDER BY id
                    """).query((rs, ignored) -> new GovernanceTask(
                            rs.getLong("id"), rs.getString("name"), rs.getString("scope_description"),
                            rs.getString("owner_name"), rs.getInt("target_quantity"),
                            rs.getInt("completed_quantity"), rs.getDate("due_date").toLocalDate(),
                            GovernanceTaskStatus.valueOf(rs.getString("status")))).list();
        }
        return List.copyOf(tasks);
    }

    public GovernanceTask create(String name, String scope, String owner, int total, LocalDate dueDate) {
        return create(name, scope, owner, total, dueDate, null);
    }

    public GovernanceTask create(String name, String scope, String owner, int total, LocalDate dueDate, String assigneeId) {
        if (databaseWritesEnabled) return createDatabaseTask(name, scope, owner, total, dueDate, assigneeId);
        var task = new GovernanceTask(nextId.getAndIncrement(), name, scope, owner, total, 0, dueDate,
                GovernanceTaskStatus.DRAFT);
        tasks.add(task);
        if (assigneeId != null && !assigneeId.isBlank()) {
            employee(assigneeId);
            assignments.put(task.id(), assigneeId);
        }
        return task;
    }

    public String assigneeId(long taskId) {
        if (databaseWritesEnabled) {
            return jdbcClient.sql("SELECT assignee_id FROM governance_task WHERE id = :id")
                    .param("id", taskId).query(String.class).optional().orElse(null);
        }
        ensureTask(taskId);
        return assignments.get(taskId);
    }

    public List<GovernanceEmployee> employees() {
        if (databaseWritesEnabled) {
            return jdbcClient.sql("""
                    SELECT code, name FROM temp_person WHERE status = 1 AND code LIKE 'emp-%' ORDER BY id
                    """).query((rs, ignored) -> new GovernanceEmployee(
                            rs.getString("code"), rs.getString("name"), "本地员工目录", "OFFICE_DIRECTORY"))
                    .list();
        }
        return employees;
    }

    private GovernanceEmployee employee(String employeeId) {
        return employees.stream().filter(item -> item.id().equals(employeeId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("员工目录身份不存在"));
    }

    public List<GovernancePlan> plans(long taskId) {
        if (databaseWritesEnabled) {
            ensureTask(taskId);
            return jdbcClient.sql("""
                    SELECT id, task_id, name, status, completed_at, start_date, due_date, actual_start,
                           actual_end, target_quantity, completed_quantity, quantity_unit,
                           responsible_user_id, dependency_ids
                    FROM governance_plan WHERE task_id = :taskId ORDER BY sequence_number, id
                    """).param("taskId", taskId).query((rs, ignored) -> new GovernancePlan(
                            rs.getLong("id"), rs.getLong("task_id"), rs.getString("name"),
                            rs.getString("status"), toLocalDate(rs.getDate("completed_at")),
                            toLocalDate(rs.getDate("start_date")), toLocalDate(rs.getDate("due_date")),
                            toLocalDate(rs.getDate("actual_start")), toLocalDate(rs.getDate("actual_end")),
                            rs.getInt("target_quantity"), rs.getInt("completed_quantity"),
                            rs.getString("quantity_unit"), rs.getString("responsible_user_id"),
                            parseLongs(rs.getString("dependency_ids")))).list();
        }
        ensureTask(taskId);
        return plans.getOrDefault(taskId, List.of());
    }

    public GovernancePlan updatePlan(long taskId, long planId, String status) {
        if (databaseWritesEnabled) return updateDatabasePlan(taskId, planId, status);
        requireStatus(taskId, GovernanceTaskStatus.IN_PROGRESS, "只有进行中的任务可以更新计划执行状态");
        if (!List.of("TODO", "IN_PROGRESS", "DONE").contains(status)) {
            throw new IllegalArgumentException("计划状态不合法");
        }
        var current = plans(taskId);
        var updated = current.stream().map(plan -> plan.id() == planId
                ? new GovernancePlan(plan.id(), plan.taskId(), plan.title(), status,
                        "DONE".equals(status) ? LocalDate.now() : null, plan.plannedStart(), plan.plannedEnd(),
                        "IN_PROGRESS".equals(status) && plan.actualStart() == null ? LocalDate.now() : plan.actualStart(),
                        "DONE".equals(status) ? LocalDate.now() : null,
                        plan.plannedQuantity(), "DONE".equals(status) ? plan.plannedQuantity() : plan.completedQuantity(),
                        plan.quantityUnit(), plan.assigneeId(), plan.dependencyIds())
                : plan).toList();
        if (updated.equals(current)) throw new IllegalArgumentException("计划项不存在");
        plans.put(taskId, updated);
        return updated.stream().filter(plan -> plan.id() == planId).findFirst().orElseThrow();
    }

    public GovernancePlan createPlan(long taskId, String title, LocalDate plannedStart, LocalDate plannedEnd,
            int plannedQuantity, String quantityUnit, String assigneeId, List<Long> dependencyIds) {
        if (databaseWritesEnabled) {
            return createDatabasePlan(taskId, title, plannedStart, plannedEnd, plannedQuantity, quantityUnit, assigneeId, dependencyIds);
        }
        requireStatus(taskId, GovernanceTaskStatus.DRAFT, "任务开始执行后计划已锁定，不能直接新增计划");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("计划名称不能为空");
        if (plannedEnd != null && plannedStart != null && plannedEnd.isBefore(plannedStart)) {
            throw new IllegalArgumentException("计划完成时间不能早于开始时间");
        }
        if (assigneeId != null && !assigneeId.isBlank()) employee(assigneeId);
        var plan = new GovernancePlan(nextPlanId.getAndIncrement(), taskId, title.trim(), "TODO", null,
                plannedStart, plannedEnd, null, null, plannedQuantity, 0, quantityUnit, assigneeId,
                dependencyIds);
        var updated = new ArrayList<>(plans(taskId));
        updated.add(plan);
        plans.put(taskId, updated);
        return plan;
    }

    public GovernanceTask updateProgress(long taskId, int completed) {
        if (databaseWritesEnabled) {
            var current = requireStatus(taskId, GovernanceTaskStatus.IN_PROGRESS, "只有进行中的任务可以更新进度");
            if (completed < 0 || completed > current.total()) throw new IllegalArgumentException("进度数量不合法");
            var status = completed == current.total() ? GovernanceTaskStatus.PENDING_CONFIRMATION : GovernanceTaskStatus.IN_PROGRESS;
            jdbcClient.sql("UPDATE governance_task SET completed_quantity = :completed, status = :status WHERE id = :id")
                    .param("completed", completed).param("status", status.name()).param("id", taskId).update();
            return task(taskId);
        }
        var current = requireStatus(taskId, GovernanceTaskStatus.IN_PROGRESS, "只有进行中的任务可以更新进度");
        if (completed < 0 || completed > current.total()) throw new IllegalArgumentException("进度数量不合法");
        var status = completed == current.total() ? GovernanceTaskStatus.PENDING_CONFIRMATION : GovernanceTaskStatus.IN_PROGRESS;
        var updated = new GovernanceTask(current.id(), current.name(), current.scope(), current.owner(),
                current.total(), completed, current.dueDate(), status);
        tasks.replaceAll(item -> item.id() == taskId ? updated : item);
        return updated;
    }

    public GovernanceTask start(long taskId) {
        var current = requireStatus(taskId, GovernanceTaskStatus.DRAFT, "只有草稿任务可以开始执行");
        var taskPlans = plans(taskId);
        if (taskPlans.isEmpty()) {
            throw new IllegalArgumentException("至少添加一项计划后才能开始执行");
        }
        var incompletePlan = taskPlans.stream().anyMatch(plan -> plan.assigneeId() == null || plan.assigneeId().isBlank()
                || plan.plannedStart() == null || plan.plannedEnd() == null || plan.plannedQuantity() <= 0);
        if (incompletePlan) {
            throw new IllegalArgumentException("所有计划都必须设置责任人、起止日期和计划数量");
        }
        if (databaseWritesEnabled) {
            jdbcClient.sql("UPDATE governance_task SET status = 'IN_PROGRESS' WHERE id = :id")
                    .param("id", taskId).update();
            return task(taskId);
        }
        var updated = new GovernanceTask(current.id(), current.name(), current.scope(), current.owner(),
                current.total(), current.completed(), current.dueDate(), GovernanceTaskStatus.IN_PROGRESS);
        tasks.replaceAll(item -> item.id() == taskId ? updated : item);
        return updated;
    }

    private GovernanceTask task(long taskId) {
        if (databaseWritesEnabled) {
            return jdbcClient.sql("""
                    SELECT id, name, scope_description, owner_name, target_quantity, completed_quantity,
                           due_date, status FROM governance_task WHERE id = :id
                    """).param("id", taskId).query((rs, ignored) -> new GovernanceTask(
                            rs.getLong("id"), rs.getString("name"), rs.getString("scope_description"),
                            rs.getString("owner_name"), rs.getInt("target_quantity"),
                            rs.getInt("completed_quantity"), rs.getDate("due_date").toLocalDate(),
                            GovernanceTaskStatus.valueOf(rs.getString("status")))).optional()
                    .orElseThrow(() -> new IllegalArgumentException("治理任务不存在"));
        }
        return tasks.stream().filter(item -> item.id() == taskId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("治理任务不存在"));
    }

    private void ensureTask(long taskId) {
        task(taskId);
    }

    private GovernanceTask requireStatus(long taskId, GovernanceTaskStatus expected, String message) {
        var task = task(taskId);
        if (task.status() != expected) throw new GovernanceTaskStateException(message);
        return task;
    }

    @Transactional
    private GovernanceTask createDatabaseTask(
            String name, String scope, String owner, int total, LocalDate dueDate, String assigneeId) {
        if (assigneeId != null && !assigneeId.isBlank()) employee(assigneeId);
        var taskNumber = "GOV-" + java.util.UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        jdbcClient.sql("""
                INSERT INTO governance_task
                    (task_number, name, status, scope_description, owner_user_id, owner_name, assignee_id,
                     due_date, target_quantity, completed_quantity, quantity_unit)
                VALUES (:number, :name, 'DRAFT', :scope, :ownerId, :ownerName, :assigneeId,
                        :dueDate, :total, 0, '资产')
                """).param("number", taskNumber).param("name", name).param("scope", scope)
                .param("ownerId", assigneeId == null || assigneeId.isBlank() ? "demo-user" : assigneeId)
                .param("ownerName", owner).param("assigneeId", assigneeId)
                .param("dueDate", dueDate).param("total", total).update();
        var id = jdbcClient.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
        return task(id);
    }

    @Transactional
    private GovernancePlan createDatabasePlan(long taskId, String title, LocalDate plannedStart,
            LocalDate plannedEnd, int plannedQuantity, String quantityUnit, String assigneeId,
            List<Long> dependencyIds) {
        requireStatus(taskId, GovernanceTaskStatus.DRAFT, "任务开始执行后计划已锁定，不能直接新增计划");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("计划名称不能为空");
        if (plannedEnd != null && plannedStart != null && plannedEnd.isBefore(plannedStart)) {
            throw new IllegalArgumentException("计划完成时间不能早于开始时间");
        }
        if (assigneeId != null && !assigneeId.isBlank()) employee(assigneeId);
        var sequence = jdbcClient.sql("SELECT COALESCE(MAX(sequence_number), 0) + 1 FROM governance_plan WHERE task_id = :taskId")
                .param("taskId", taskId).query(Integer.class).single();
        jdbcClient.sql("""
                INSERT INTO governance_plan
                    (task_id, sequence_number, name, responsible_user_id, start_date, due_date,
                     target_quantity, completed_quantity, quantity_unit, status, dependency_ids)
                VALUES (:taskId, :sequence, :name, :assigneeId, :startDate, :dueDate,
                        :quantity, 0, :unit, 'TODO', :dependencies)
                """).param("taskId", taskId).param("sequence", sequence).param("name", title.trim())
                .param("assigneeId", assigneeId).param("startDate", plannedStart).param("dueDate", plannedEnd)
                .param("quantity", plannedQuantity).param("unit", quantityUnit == null ? "项" : quantityUnit)
                .param("dependencies", writeJson(dependencyIds)).update();
        var id = jdbcClient.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();
        return plans(taskId).stream().filter(plan -> plan.id() == id).findFirst().orElseThrow();
    }

    @Transactional
    private GovernancePlan updateDatabasePlan(long taskId, long planId, String status) {
        requireStatus(taskId, GovernanceTaskStatus.IN_PROGRESS, "只有进行中的任务可以更新计划执行状态");
        if (!List.of("TODO", "IN_PROGRESS", "DONE").contains(status)) throw new IllegalArgumentException("计划状态不合法");
        var current = plans(taskId).stream().filter(plan -> plan.id() == planId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("计划项不存在"));
        var completed = "DONE".equals(status) ? current.plannedQuantity() : current.completedQuantity();
        jdbcClient.sql("""
                UPDATE governance_plan SET status = :status, completed_quantity = :completed,
                    actual_start = CASE WHEN :status = 'IN_PROGRESS' AND actual_start IS NULL THEN CURRENT_DATE ELSE actual_start END,
                    completed_at = CASE WHEN :status = 'DONE' THEN CURRENT_DATE ELSE NULL END,
                    actual_end = CASE WHEN :status = 'DONE' THEN CURRENT_DATE ELSE NULL END
                WHERE id = :planId AND task_id = :taskId
                """).param("status", status).param("completed", completed)
                .param("planId", planId).param("taskId", taskId).update();
        return plans(taskId).stream().filter(plan -> plan.id() == planId).findFirst().orElseThrow();
    }

    private LocalDate toLocalDate(java.sql.Date value) {
        return value == null ? null : value.toLocalDate();
    }

    private List<Long> parseLongs(String json) {
        try {
            return json == null || json.isBlank() || objectMapper == null
                    ? List.of()
                    : objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String writeJson(List<Long> values) {
        try {
            return objectMapper == null ? "[]" : objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception exception) {
            throw new IllegalArgumentException("计划依赖无法保存", exception);
        }
    }
}
