package com.tianshu.assets.governance.task.application;

import com.tianshu.assets.governance.application.GovernanceTaskStateException;
import com.tianshu.assets.governance.application.GovernanceValidationException;
import com.tianshu.assets.governance.domain.GovernanceEmployee;
import com.tianshu.assets.governance.issue.application.GovernanceIssueStore;
import com.tianshu.assets.governance.issue.domain.GovernanceIssueStatus;
import com.tianshu.assets.governance.task.domain.GovernancePlan;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import com.tianshu.assets.governance.task.domain.GovernanceWorkflowVersion;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GovernanceTaskApplicationService {

    private final GovernanceTaskStore store;
    private final GovernanceEmployeeDirectory employeeDirectory;
    private final GovernanceIssueStore issueStore;

    @Autowired
    public GovernanceTaskApplicationService(
            GovernanceTaskStore store, GovernanceEmployeeDirectory employeeDirectory,
            GovernanceIssueStore issueStore) {
        this.store = store;
        this.employeeDirectory = employeeDirectory;
        this.issueStore = issueStore;
    }

    public GovernanceTaskApplicationService(
            GovernanceTaskStore store, GovernanceEmployeeDirectory employeeDirectory) {
        this(store, employeeDirectory, null);
    }

    public GovernanceTaskApplicationService(GovernanceTaskStore store) {
        this(store, store instanceof GovernanceEmployeeDirectory directory ? directory : List::of, null);
    }

    public List<GovernanceTask> list() {
        return store.findAll();
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
}
