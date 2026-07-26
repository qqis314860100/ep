package com.tianshu.assets.governance.task.application;

import com.tianshu.assets.governance.application.GovernanceTaskStateException;
import com.tianshu.assets.governance.application.GovernanceValidationException;
import com.tianshu.assets.governance.domain.GovernanceEmployee;
import com.tianshu.assets.governance.issue.application.GovernanceIssueStore;
import com.tianshu.assets.governance.issue.domain.GovernanceIssueStatus;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionStore;
import com.tianshu.assets.governance.execution.domain.GovernanceItem;
import com.tianshu.assets.governance.execution.domain.GovernanceItemStatus;
import com.tianshu.assets.governance.task.domain.GovernancePlan;
import com.tianshu.assets.governance.task.domain.GovernancePlanStatus;
import com.tianshu.assets.governance.task.domain.GovernanceProgress;
import com.tianshu.assets.governance.task.domain.GovernanceRuleSnapshot;
import com.tianshu.assets.governance.task.domain.GovernanceScopeSnapshot;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import com.tianshu.assets.governance.task.domain.GovernanceWorkflowVersion;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GovernanceTaskApplicationService {

    private final GovernanceTaskStore store;
    private final GovernanceEmployeeDirectory employeeDirectory;
    private final GovernanceIssueStore issueStore;
    private final GovernanceWorkflowStore workflowStore;
    private final GovernanceExecutionStore executionStore;

    @Autowired
    public GovernanceTaskApplicationService(
            GovernanceTaskStore store, GovernanceEmployeeDirectory employeeDirectory,
            GovernanceIssueStore issueStore,
            GovernanceWorkflowStore workflowStore,
            GovernanceExecutionStore executionStore) {
        this.store = store;
        this.employeeDirectory = employeeDirectory;
        this.issueStore = issueStore;
        this.workflowStore = workflowStore;
        this.executionStore = executionStore;
    }

    public GovernanceTaskApplicationService(
            GovernanceTaskStore store, GovernanceEmployeeDirectory employeeDirectory,
            GovernanceIssueStore issueStore) {
        this(store, employeeDirectory, issueStore, null, null);
    }

    public GovernanceTaskApplicationService(
            GovernanceTaskStore store, GovernanceEmployeeDirectory employeeDirectory) {
        this(store, employeeDirectory, null, null, null);
    }

    public GovernanceTaskApplicationService(GovernanceTaskStore store) {
        this(store, store instanceof GovernanceEmployeeDirectory directory ? directory : List::of,
                null, null, null);
    }

    public List<GovernanceTask> list() {
        return store.findAll();
    }

    public List<TaskProjection> list(TaskFilter filter) {
        var effective = filter == null ? TaskFilter.empty() : filter;
        return store.findAll().stream()
                .filter(task -> effective.status() == null || task.status() == effective.status())
                .filter(task -> effective.ownerUserId() == null
                        || effective.ownerUserId().equals(task.ownerUserId()))
                .filter(task -> effective.dueBefore() == null || task.dueDate() != null
                        && !task.dueDate().isAfter(effective.dueBefore()))
                .filter(task -> matchesItemScope(task, effective))
                .map(this::projection)
                .toList();
    }

    public GovernanceTask get(long taskId) {
        return store.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("治理任务不存在"));
    }

    public List<GovernancePlan> plans(long taskId) {
        get(taskId);
        return store.findPlans(taskId);
    }

    public List<GovernanceEmployee> employees() {
        return employeeDirectory.findAllEmployees();
    }

    public TaskProjection detail(long taskId) {
        return projection(get(taskId));
    }

    @Transactional
    public GovernanceTask submitForConfirmation(long taskId, long expectedVersion) {
        synchronized (store) {
            var task = requireClosedLoop(taskId);
            if (task.status() != GovernanceTaskStatus.IN_PROGRESS) {
                throw new GovernanceTaskStateException("只有进行中的治理任务可以提交确认");
            }
            if (task.version() != expectedVersion) {
                throw new GovernanceTaskStateException("治理任务已被其他用户更新，请刷新后重试");
            }
            var items = requireExecutionStore().items(taskId);
            if (items.isEmpty() || items.stream().anyMatch(item -> !item.status().countsAsSubmitted()
                    || item.status() == GovernanceItemStatus.BLOCKED
                    || item.status() == GovernanceItemStatus.REWORK_REQUIRED)) {
                throw new GovernanceValidationException("仍有阻塞或未提交治理项");
            }
            var requested = copyWithStatus(task, task.status().moveTo(GovernanceTaskStatus.PENDING_CONFIRMATION));
            return store.update(requested, expectedVersion);
        }
    }

    private TaskProjection projection(GovernanceTask task) {
        if (task.workflowVersion() == GovernanceWorkflowVersion.LEGACY_PROGRESS) {
            var legacyProgress = new GovernanceProgress(
                    task.legacyTotal(), task.legacyCompleted(), 0, 0, 0, 0);
            return new TaskProjection(task, legacyProgress, 0,
                    store.findPlans(task.id()).stream().map(plan -> new PlanProjection(
                            plan, plan.planStatus(), plan.completedQuantity())).toList(),
                    null, null, Map.of());
        }
        if (task.scopeSnapshotId() == null || executionStore == null || workflowStore == null) {
            return new TaskProjection(task, GovernanceProgress.from(List.of()), 0,
                    store.findPlans(task.id()).stream().map(plan -> new PlanProjection(
                            plan, GovernancePlanStatus.NOT_STARTED, 0)).toList(),
                    null, null, workbenchEntries(task.id()));
        }
        var items = executionStore.items(task.id());
        var progress = GovernanceProgress.from(items.stream().map(GovernanceItem::status).toList());
        var projectedPlans = projectPlans(store.findPlans(task.id()), items);
        var snapshot = workflowStore.scopeSnapshotForTask(task.id());
        return new TaskProjection(task, progress, progress.blocked() + progress.reworkRequired(),
                projectedPlans, snapshot, snapshot.ruleSnapshot(), workbenchEntries(task.id()));
    }

    private List<PlanProjection> projectPlans(List<GovernancePlan> plans, List<GovernanceItem> items) {
        var ownStatuses = new LinkedHashMap<Long, GovernancePlanStatus>();
        var completed = new LinkedHashMap<Long, Integer>();
        for (var plan : plans) {
            var planItems = items.stream().filter(item -> item.planId() == plan.id()).toList();
            var submitted = (int) planItems.stream().filter(item -> item.status().countsAsSubmitted()).count();
            completed.put(plan.id(), submitted);
            var status = !planItems.isEmpty() && submitted == planItems.size()
                    ? GovernancePlanStatus.DONE
                    : planItems.stream().anyMatch(item -> item.status() != GovernanceItemStatus.PENDING)
                            ? GovernancePlanStatus.IN_PROGRESS
                            : GovernancePlanStatus.NOT_STARTED;
            ownStatuses.put(plan.id(), status);
        }
        var derivedStatuses = new LinkedHashMap<>(ownStatuses);
        boolean changed;
        do {
            changed = false;
            var previousStatuses = derivedStatuses;
            var nextStatuses = new LinkedHashMap<Long, GovernancePlanStatus>();
            for (var plan : plans) {
                var dependenciesDone = plan.dependencyIds().stream()
                        .allMatch(id -> previousStatuses.get(id) == GovernancePlanStatus.DONE);
                var status = dependenciesDone
                        ? ownStatuses.get(plan.id())
                        : GovernancePlanStatus.BLOCKED;
                nextStatuses.put(plan.id(), status);
                changed |= status != previousStatuses.get(plan.id());
            }
            derivedStatuses = nextStatuses;
        } while (changed);
        var finalStatuses = derivedStatuses;
        return plans.stream().map(plan -> {
            return new PlanProjection(
                    plan, finalStatuses.get(plan.id()), completed.getOrDefault(plan.id(), 0));
        }).toList();
    }

    private boolean matchesItemScope(GovernanceTask task, TaskFilter filter) {
        if (filter.field() == null && (filter.scopeFingerprint() == null || filter.scopeFingerprint().isBlank())) {
            return true;
        }
        if (task.workflowVersion() != GovernanceWorkflowVersion.CLOSED_LOOP_V1 || executionStore == null) {
            return false;
        }
        return executionStore.items(task.id()).stream().anyMatch(item ->
                (filter.field() == null || item.targetField() == filter.field())
                        && (filter.scopeFingerprint() == null || filter.scopeFingerprint().isBlank()
                                || filter.scopeFingerprint().equals(item.scopeFingerprint())));
    }

    private GovernanceExecutionStore requireExecutionStore() {
        if (executionStore == null) throw new IllegalStateException("治理执行存储未配置");
        return executionStore;
    }

    private GovernanceTask copyWithStatus(GovernanceTask task, GovernanceTaskStatus status) {
        return new GovernanceTask(
                task.id(), task.taskNumber(), task.name(), task.actionType(), task.issueType(),
                task.ownerUserId(), task.ownerName(), task.assigneeId(), task.dueDate(), status,
                task.currentRound(), task.workflowVersion(), task.scopeSnapshotId(),
                task.qualityPolicySnapshotId(), task.legacyTotal(), task.legacyCompleted(), task.version());
    }

    private Map<String, String> workbenchEntries(long taskId) {
        return Map.of(
                "execution", "/sys/drawing/tasks/" + taskId + "/execute",
                "confirmation", "/sys/drawing/tasks/" + taskId + "/confirm",
                "acceptance", "/sys/drawing/tasks/" + taskId + "/accept");
    }

    public GovernanceTask requireClosedLoop(long taskId) {
        var task = get(taskId);
        if (task.workflowVersion() == GovernanceWorkflowVersion.LEGACY_PROGRESS) {
            throw new GovernanceTaskStateException(GovernanceTaskStateException.LEGACY_READ_ONLY_MESSAGE);
        }
        return task;
    }

    public GovernanceTask rejectTaskCreation() {
        throw new GovernanceTaskStateException(GovernanceTaskStateException.LEGACY_READ_ONLY_MESSAGE);
    }

    public GovernanceTask rejectTaskMutation(long taskId) {
        requireClosedLoop(taskId);
        throw new GovernanceTaskStateException(GovernanceTaskStateException.LEGACY_READ_ONLY_MESSAGE);
    }

    public GovernancePlan rejectPlanMutation(long taskId) {
        requireClosedLoop(taskId);
        throw new GovernanceTaskStateException(GovernanceTaskStateException.LEGACY_READ_ONLY_MESSAGE);
    }

    public GovernancePlan createPlan(long taskId, CreatePlanCommand command) {
        synchronized (store) {
            var task = requireClosedLoop(taskId);
            if (task.status() != GovernanceTaskStatus.DRAFT) {
                throw new GovernanceTaskStateException("治理任务启动后计划已锁定");
            }
            var errors = validatePlanCommand(command);
            if (!errors.isEmpty()) throw new GovernanceValidationException(errors);
            if (issueStore == null) throw new IllegalStateException("治理问题存储未配置");

            var issues = issueStore.findByIds(command.issueIds());
            if (issues.size() != command.issueIds().size()
                    || issues.stream().anyMatch(issue -> issue.status() != GovernanceIssueStatus.CLAIMED
                            || issue.taskId() == null || issue.taskId() != taskId)) {
                throw new GovernanceValidationException("计划治理项必须由当前任务领取");
            }
            var taskPlanIds = store.findPlans(taskId).stream().map(GovernancePlan::id).collect(
                    java.util.stream.Collectors.toSet());
            if (command.id() > 0 && command.dependencyIds().contains(command.id())) {
                throw new GovernanceValidationException("计划不能依赖自身");
            }
            if (!taskPlanIds.containsAll(command.dependencyIds())) {
                throw new GovernanceValidationException("前置计划必须属于同一治理任务");
            }
            return store.insertPlan(GovernancePlan.closedLoop(
                    command.id(), taskId, 0, command.title(), command.responsibleUserId(),
                    command.plannedStart(), command.plannedEnd(), command.dependencyIds(),
                    command.issueIds(), 0));
        }
    }

    private List<String> validatePlanCommand(CreatePlanCommand command) {
        var errors = new java.util.ArrayList<String>();
        if (command == null) return List.of("治理计划不能为空");
        if (command.title() == null || command.title().isBlank()) errors.add("计划名称不能为空");
        if (command.responsibleUserId() == null || command.responsibleUserId().isBlank()) {
            errors.add("计划责任人不能为空");
        }
        if (command.plannedStart() == null || command.plannedEnd() == null) errors.add("计划起止日期不能为空");
        if (command.plannedStart() != null && command.plannedEnd() != null
                && command.plannedEnd().isBefore(command.plannedStart())) {
            errors.add("计划结束日期不能早于开始日期");
        }
        if (command.issueIds() == null || command.issueIds().isEmpty()) {
            errors.add("计划治理项不能为空");
        } else if (command.issueIds().stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(command.issueIds()).size() != command.issueIds().size()) {
            errors.add("计划治理项不能为空且不能重复");
        }
        if (command.dependencyIds() != null
                && new HashSet<>(command.dependencyIds()).size() != command.dependencyIds().size()) {
            errors.add("前置计划不能重复");
        }
        return errors;
    }

    public record CreatePlanCommand(
            long id,
            String title,
            LocalDate plannedStart,
            LocalDate plannedEnd,
            String responsibleUserId,
            List<Long> dependencyIds,
            List<Long> issueIds) {
        public CreatePlanCommand {
            dependencyIds = dependencyIds == null ? List.of() : List.copyOf(dependencyIds);
            issueIds = issueIds == null ? List.of() : List.copyOf(issueIds);
        }
    }

    public record TaskFilter(
            GovernanceTaskStatus status,
            String ownerUserId,
            LocalDate dueBefore,
            GovernanceField field,
            String scopeFingerprint) {
        public static TaskFilter empty() {
            return new TaskFilter(null, null, null, null, null);
        }
    }

    public record PlanProjection(
            GovernancePlan plan, GovernancePlanStatus status, int completedQuantity) {}

    public record TaskProjection(
            GovernanceTask task,
            GovernanceProgress progress,
            int riskCount,
            List<PlanProjection> plans,
            GovernanceScopeSnapshot scopeSnapshot,
            GovernanceRuleSnapshot ruleSnapshot,
            Map<String, String> workbenchEntries) {
        public TaskProjection {
            plans = List.copyOf(plans);
            workbenchEntries = Map.copyOf(workbenchEntries);
        }
    }
}
