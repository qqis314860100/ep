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
import org.springframework.stereotype.Service;

@Service
public class GovernanceTaskService {

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
        return List.copyOf(tasks);
    }

    public GovernanceTask create(String name, String scope, String owner, int total, LocalDate dueDate) {
        return create(name, scope, owner, total, dueDate, null);
    }

    public GovernanceTask create(String name, String scope, String owner, int total, LocalDate dueDate, String assigneeId) {
        var task = new GovernanceTask(nextId.getAndIncrement(), name, scope, owner, total, 0, dueDate,
                GovernanceTaskStatus.IN_PROGRESS);
        tasks.add(task);
        if (assigneeId != null && !assigneeId.isBlank()) {
            employee(assigneeId);
            assignments.put(task.id(), assigneeId);
        }
        return task;
    }

    public String assigneeId(long taskId) {
        ensureTask(taskId);
        return assignments.get(taskId);
    }

    public List<GovernanceEmployee> employees() {
        return employees;
    }

    private GovernanceEmployee employee(String employeeId) {
        return employees.stream().filter(item -> item.id().equals(employeeId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("员工目录身份不存在"));
    }

    public List<GovernancePlan> plans(long taskId) {
        ensureTask(taskId);
        return plans.getOrDefault(taskId, List.of());
    }

    public GovernancePlan updatePlan(long taskId, long planId, String status) {
        ensureTask(taskId);
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
        ensureTask(taskId);
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
        var current = task(taskId);
        if (completed < 0 || completed > current.total()) throw new IllegalArgumentException("进度数量不合法");
        var status = completed == current.total() ? GovernanceTaskStatus.PENDING_CONFIRMATION : GovernanceTaskStatus.IN_PROGRESS;
        var updated = new GovernanceTask(current.id(), current.name(), current.scope(), current.owner(),
                current.total(), completed, current.dueDate(), status);
        tasks.replaceAll(item -> item.id() == taskId ? updated : item);
        return updated;
    }

    private GovernanceTask task(long taskId) {
        return tasks.stream().filter(item -> item.id() == taskId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("治理任务不存在"));
    }

    private void ensureTask(long taskId) {
        task(taskId);
    }
}
