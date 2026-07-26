package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.governance.application.GovernanceTaskStateException;
import com.tianshu.assets.governance.task.application.GovernanceTaskStore;
import com.tianshu.assets.governance.task.domain.GovernancePlan;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import com.tianshu.assets.governance.task.domain.GovernanceWorkflowVersion;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryGovernanceTaskStore implements GovernanceTaskStore {

    private final Map<Long, GovernanceTask> tasks = new ConcurrentHashMap<>();
    private final Map<Long, List<GovernancePlan>> plans = new ConcurrentHashMap<>();
    private final AtomicLong nextTaskId = new AtomicLong(4);
    private final AtomicLong nextPlanId = new AtomicLong(401);
    public static InMemoryGovernanceTaskStore withLegacySeed() {
        var store = new InMemoryGovernanceTaskStore();
        store.seedLegacyData();
        return store;
    }

    @Override
    public List<GovernanceTask> findAll() {
        return tasks.values().stream().sorted(Comparator.comparingLong(GovernanceTask::id)).toList();
    }

    @Override
    public Optional<GovernanceTask> findById(long taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    @Override
    public GovernanceTask insert(GovernanceTask task) {
        var id = task.id() > 0 ? task.id() : nextTaskId.getAndIncrement();
        var created = copyTask(task, id, 0);
        if (tasks.putIfAbsent(id, created) != null) {
            throw new GovernanceTaskStateException("治理任务已存在");
        }
        nextTaskId.accumulateAndGet(id + 1, Math::max);
        return created;
    }

    @Override
    public GovernanceTask update(GovernanceTask task, long expectedVersion) {
        return tasks.compute(task.id(), (id, current) -> {
            if (current == null) throw new IllegalArgumentException("治理任务不存在");
            if (current.workflowVersion() == GovernanceWorkflowVersion.LEGACY_PROGRESS) {
                throw new GovernanceTaskStateException(GovernanceTaskStateException.LEGACY_READ_ONLY_MESSAGE);
            }
            if (current.version() != expectedVersion) {
                throw new GovernanceTaskStateException("治理任务已被其他用户更新，请刷新后重试");
            }
            return current.applyMutableState(task, expectedVersion + 1);
        });
    }

    @Override
    public synchronized List<GovernancePlan> findPlans(long taskId) {
        return plans.getOrDefault(taskId, List.of()).stream()
                .sorted(Comparator.comparingInt(GovernancePlan::sequence).thenComparingLong(GovernancePlan::id))
                .toList();
    }

    @Override
    public synchronized GovernancePlan insertPlan(GovernancePlan plan) {
        var task = findById(plan.taskId())
                .orElseThrow(() -> new IllegalArgumentException("治理任务不存在"));
        if (task.workflowVersion() == GovernanceWorkflowVersion.LEGACY_PROGRESS) {
            throw new GovernanceTaskStateException(GovernanceTaskStateException.LEGACY_READ_ONLY_MESSAGE);
        }
        if (task.status() != GovernanceTaskStatus.DRAFT) {
            throw new GovernanceTaskStateException("治理任务启动后计划已锁定");
        }
        var id = plan.id() > 0 ? plan.id() : nextPlanId.getAndIncrement();
        var current = plans.getOrDefault(plan.taskId(), List.of());
        if (current.stream().anyMatch(item -> item.id() == id)) {
            throw new GovernanceTaskStateException("治理计划已存在");
        }
        var sequence = plan.sequence() > 0
                ? plan.sequence()
                : current.stream().mapToInt(GovernancePlan::sequence).max().orElse(0) + 1;
        var created = copyPlan(plan, id, sequence, 0);
        var updated = new ArrayList<>(current);
        updated.add(created);
        plans.put(plan.taskId(), List.copyOf(updated));
        nextPlanId.accumulateAndGet(id + 1, Math::max);
        return created;
    }

    private GovernanceTask copyTask(GovernanceTask task, long id, long version) {
        return new GovernanceTask(id, task.taskNumber(), task.name(), task.actionType(), task.issueType(),
                task.ownerUserId(), task.ownerName(), task.assigneeId(), task.dueDate(), task.status(),
                task.currentRound(), task.workflowVersion(), task.scopeSnapshotId(), task.qualityPolicySnapshotId(),
                task.legacyTotal(), task.legacyCompleted(), version);
    }

    private GovernancePlan copyPlan(GovernancePlan plan, long id, int sequence, long version) {
        return new GovernancePlan(id, plan.taskId(), sequence, plan.title(), plan.planStatus(), plan.status(), plan.completedAt(),
                plan.plannedStart(), plan.plannedEnd(), plan.actualStart(), plan.actualEnd(),
                plan.plannedQuantity(), plan.completedQuantity(), plan.quantityUnit(), plan.assigneeId(),
                plan.responsibleUserId(), plan.dependencyIds(), plan.issueIds(), version);
    }

    private void seedLegacyData() {
        tasks.put(1L, legacyTask(1, "GOV-2026-001", "A 拉线历史数模范围补充", "旧拉线：XM-PL01、A线",
                "emp-chen", "陈工", LocalDate.of(2026, 8, 15), GovernanceTaskStatus.IN_PROGRESS, 286, 174));
        tasks.put(2L, legacyTask(2, "GOV-2026-002", "历史专业类别标准化", "机械、电气自由文本",
                "emp-li", "李工", LocalDate.of(2026, 7, 31), GovernanceTaskStatus.PENDING_CONFIRMATION, 421, 421));
        tasks.put(3L, legacyTask(3, "GOV-2026-003", "失效文件引用治理", "无法访问的对象存储文件",
                "emp-wang", "王工", LocalDate.of(2026, 7, 25), GovernanceTaskStatus.COMPLETED, 37, 37));

        plans.put(1L, List.of(
                legacyPlan(101, 1, "导出历史模组资产清单", "DONE", LocalDate.of(2026, 7, 10),
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 8, 2), 286, 286, "个资产", "emp-chen", List.of()),
                legacyPlan(102, 1, "补充平台、基地和拉线范围", "IN_PROGRESS", null,
                        LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 9), LocalDate.of(2026, 8, 3),
                        null, 286, 174, "个资产", "emp-chen", List.of(101L)),
                legacyPlan(103, 1, "提交业务专家确认", "TODO", null,
                        LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12), null, null,
                        174, 0, "个资产", "emp-li", List.of(102L))));
        plans.put(2L, List.of(
                legacyPlan(201, 2, "整理历史专业自由文本", "DONE", LocalDate.of(2026, 7, 12),
                        LocalDate.of(2026, 7, 8), LocalDate.of(2026, 7, 12), LocalDate.of(2026, 7, 8),
                        LocalDate.of(2026, 7, 12), 421, 421, "个字段", "emp-li", List.of()),
                legacyPlan(202, 2, "提交标准化结果确认", "DONE", LocalDate.of(2026, 7, 15),
                        LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 13),
                        LocalDate.of(2026, 7, 15), 421, 421, "个字段", "emp-li", List.of(201L))));
        plans.put(3L, List.of(
                legacyPlan(301, 3, "检查对象存储文件可访问性", "DONE", LocalDate.of(2026, 7, 17),
                        LocalDate.of(2026, 7, 16), LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 16),
                        LocalDate.of(2026, 7, 17), 37, 37, "个文件", "emp-wang", List.of()),
                legacyPlan(302, 3, "登记失效引用处理结论", "DONE", LocalDate.of(2026, 7, 18),
                        LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 18),
                        LocalDate.of(2026, 7, 18), 37, 37, "个文件", "emp-wang", List.of(301L))));
    }

    private GovernanceTask legacyTask(long id, String number, String name, String scope, String ownerId,
            String ownerName, LocalDate dueDate, GovernanceTaskStatus status, int total, int completed) {
        return new GovernanceTask(id, number, name, scope, "LEGACY_MANUAL_PROGRESS", ownerId, ownerName,
                ownerId, dueDate, status, 0, GovernanceWorkflowVersion.LEGACY_PROGRESS, null, null,
                total, completed, 0);
    }

    private GovernancePlan legacyPlan(long id, long taskId, String title, String status, LocalDate completedAt,
            LocalDate plannedStart, LocalDate plannedEnd, LocalDate actualStart, LocalDate actualEnd,
            int plannedQuantity, int completedQuantity, String quantityUnit, String assigneeId,
            List<Long> dependencyIds) {
        return new GovernancePlan(id, taskId, title, status, completedAt, plannedStart, plannedEnd,
                actualStart, actualEnd, plannedQuantity, completedQuantity, quantityUnit, assigneeId,
                dependencyIds, 0);
    }
}
