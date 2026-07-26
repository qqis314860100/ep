package com.tianshu.assets.governance.task.application;

import com.tianshu.assets.governance.application.GovernanceTaskStateException;
import com.tianshu.assets.governance.application.GovernanceValidationException;
import com.tianshu.assets.governance.execution.domain.GovernanceItem;
import com.tianshu.assets.governance.execution.domain.GovernanceItemStatus;
import com.tianshu.assets.governance.issue.application.GovernanceIssueStore;
import com.tianshu.assets.governance.issue.domain.GovernanceIssue;
import com.tianshu.assets.governance.issue.domain.GovernanceIssueStatus;
import com.tianshu.assets.governance.task.domain.GovernancePlan;
import com.tianshu.assets.governance.task.domain.GovernanceScopeItem;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import com.tianshu.assets.governance.task.domain.GovernanceWorkflowVersion;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GovernanceTaskStartService {

    private final GovernanceTaskStore taskStore;
    private final GovernanceIssueStore issueStore;
    private final GovernanceWorkflowStore workflowStore;
    private final GovernanceRuleCatalog ruleCatalog;
    private final Clock clock;

    public GovernanceTaskStartService(
            GovernanceTaskStore taskStore,
            GovernanceIssueStore issueStore,
            GovernanceWorkflowStore workflowStore,
            GovernanceRuleCatalog ruleCatalog) {
        this(taskStore, issueStore, workflowStore, ruleCatalog, Clock.systemUTC());
    }

    public GovernanceTaskStartService(
            GovernanceTaskStore taskStore,
            GovernanceIssueStore issueStore,
            GovernanceWorkflowStore workflowStore,
            GovernanceRuleCatalog ruleCatalog,
            Clock clock) {
        this.taskStore = taskStore;
        this.issueStore = issueStore;
        this.workflowStore = workflowStore;
        this.ruleCatalog = ruleCatalog;
        this.clock = clock;
    }

    @Transactional
    public GovernanceTask start(long taskId, long expectedVersion, String actorUserId) {
        if (actorUserId == null || actorUserId.isBlank()) {
            throw new GovernanceValidationException("启动人不能为空");
        }
        synchronized (taskStore) {
            var task = requireStartable(taskId, expectedVersion);
            var plans = taskStore.findPlans(taskId);
            var claimedIssues = issueStore.findClaimedByTask(taskId);
            var errors = validateStructure(taskId, plans, claimedIssues);
            if (!errors.isEmpty()) throw new GovernanceValidationException(errors);

            var issueIds = claimedIssues.stream().map(GovernanceIssue::id).toList();
            var reloadedIssues = issueStore.findByIds(issueIds);
            validateReloadedIssues(taskId, claimedIssues, reloadedIssues);
            var rules = ruleCatalog.enabledSnapshot();
            if (reloadedIssues.stream().anyMatch(issue -> issue.ruleVersion() != rules.fieldRuleVersion())) {
                throw new GovernanceValidationException("治理问题规则版本已变化，请重新生成问题");
            }

            var planByIssue = indexPlansByIssue(plans);
            var scopeItems = new ArrayList<GovernanceScopeItem>(reloadedIssues.size());
            var items = new ArrayList<GovernanceItem>(reloadedIssues.size());
            for (var issue : reloadedIssues) {
                var plan = planByIssue.get(issue.id());
                var fingerprint = issue.scopeFingerprint() == null || issue.scopeFingerprint().isBlank()
                        ? issue.fingerprint()
                        : issue.scopeFingerprint();
                scopeItems.add(new GovernanceScopeItem(
                        0, taskId, plan.id(), issue.id(), issue.assetId(), issue.targetField(),
                        issue.targetPath(), issue.originalFactJson(), issue.assetVersion(), issue.ruleVersion(),
                        fingerprint, plan.responsibleUserId()));
                items.add(new GovernanceItem(
                        0, taskId, plan.id(), issue.id(), issue.assetId(), issue.targetField(),
                        task.actionType(), plan.responsibleUserId(), GovernanceItemStatus.PENDING,
                        issue.assetVersion(), task.currentRound(), fingerprint, 0, null, null, null));
            }

            var frozen = workflowStore.freeze(new GovernanceWorkflowStore.FreezeCommand(
                    taskId, issueIds,
                    reloadedIssues.stream().map(GovernanceIssue::assetId).distinct().toList(),
                    rules, actorUserId, Instant.now(clock), scopeItems, items));
            try {
                var requested = startedTask(task, frozen.scopeSnapshotId(), frozen.qualityPolicySnapshotId());
                return taskStore.update(requested, expectedVersion);
            } catch (RuntimeException exception) {
                workflowStore.discard(frozen.scopeSnapshotId());
                throw exception;
            }
        }
    }

    public GovernanceTask start(long taskId, long expectedVersion) {
        return start(taskId, expectedVersion, "system");
    }

    private GovernanceTask requireStartable(long taskId, long expectedVersion) {
        var task = taskStore.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("治理任务不存在"));
        if (task.workflowVersion() != GovernanceWorkflowVersion.CLOSED_LOOP_V1) {
            throw new GovernanceTaskStateException(GovernanceTaskStateException.LEGACY_READ_ONLY_MESSAGE);
        }
        if (task.status() != GovernanceTaskStatus.DRAFT) {
            throw new GovernanceTaskStateException("只有草稿治理任务可以启动");
        }
        if (task.version() != expectedVersion) {
            throw new GovernanceTaskStateException("治理任务已被其他用户更新，请刷新后重试");
        }
        return task;
    }

    private List<String> validateStructure(
            long taskId, List<GovernancePlan> plans, List<GovernanceIssue> claimedIssues) {
        var errors = new LinkedHashSet<String>();
        if (plans.isEmpty()) return List.of("至少需要一个治理计划");
        for (var plan : plans) {
            if (plan.responsibleUserId() == null || plan.responsibleUserId().isBlank()) {
                errors.add("每个计划都必须指定责任人");
            }
            if (plan.plannedStart() == null || plan.plannedEnd() == null
                    || plan.plannedEnd().isBefore(plan.plannedStart())) {
                errors.add("每个计划都必须设置有效起止日期");
            }
            if (plan.issueIds().isEmpty()) errors.add("每个计划都必须分配治理项");
        }

        var claimedIds = claimedIssues.stream().map(GovernanceIssue::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var assignmentCounts = new LinkedHashMap<Long, Integer>();
        plans.forEach(plan -> plan.issueIds().forEach(
                issueId -> assignmentCounts.merge(issueId, 1, Integer::sum)));
        var missing = claimedIds.stream().filter(id -> assignmentCounts.getOrDefault(id, 0) == 0).toList();
        if (!missing.isEmpty()) errors.add("治理项必须全部分配: " + missing);
        var duplicates = assignmentCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1).map(Map.Entry::getKey).sorted().toList();
        if (!duplicates.isEmpty()) errors.add("治理项不能重复分配: " + duplicates);
        var unclaimed = assignmentCounts.keySet().stream().filter(id -> !claimedIds.contains(id)).sorted().toList();
        if (!unclaimed.isEmpty()) errors.add("计划包含未由当前任务领取的治理项: " + unclaimed);

        var planIds = plans.stream().map(GovernancePlan::id).collect(Collectors.toSet());
        for (var plan : plans) {
            if (plan.dependencyIds().contains(plan.id())) errors.add("计划不能依赖自身: " + plan.id());
            var invalid = plan.dependencyIds().stream().filter(id -> !planIds.contains(id)).sorted().toList();
            if (!invalid.isEmpty()) errors.add("前置计划必须属于同一治理任务: " + invalid);
            if (plan.taskId() != taskId) errors.add("治理计划必须属于当前任务: " + plan.id());
        }
        if (hasCycle(plans, planIds)) errors.add("计划依赖不能形成环");
        return List.copyOf(errors);
    }

    private boolean hasCycle(List<GovernancePlan> plans, Set<Long> planIds) {
        var remainingDependencies = new HashMap<Long, Integer>();
        var dependents = new HashMap<Long, List<Long>>();
        for (var plan : plans) {
            var dependencies = plan.dependencyIds().stream()
                    .filter(planIds::contains)
                    .distinct()
                    .toList();
            remainingDependencies.put(plan.id(), dependencies.size());
            for (var dependencyId : dependencies) {
                dependents.computeIfAbsent(dependencyId, ignored -> new ArrayList<>()).add(plan.id());
            }
        }
        var ready = new ArrayDeque<Long>();
        remainingDependencies.forEach((planId, count) -> {
            if (count == 0) ready.add(planId);
        });
        var processed = 0;
        while (!ready.isEmpty()) {
            var planId = ready.removeFirst();
            processed++;
            for (var dependentId : dependents.getOrDefault(planId, List.of())) {
                var remaining = remainingDependencies.computeIfPresent(
                        dependentId, (ignored, count) -> count - 1);
                if (remaining != null && remaining == 0) ready.addLast(dependentId);
            }
        }
        return processed != planIds.size();
    }

    private void validateReloadedIssues(
            long taskId, List<GovernanceIssue> expected, List<GovernanceIssue> reloaded) {
        var byId = reloaded.stream().collect(Collectors.toMap(GovernanceIssue::id, Function.identity()));
        var changed = expected.stream().anyMatch(issue -> {
            var current = byId.get(issue.id());
            return current == null
                    || current.status() != GovernanceIssueStatus.CLAIMED
                    || current.taskId() == null
                    || current.taskId() != taskId
                    || current.version() != issue.version()
                    || current.assetVersion() != issue.assetVersion()
                    || current.ruleVersion() != issue.ruleVersion();
        });
        if (changed || reloaded.size() != expected.size()) {
            throw new GovernanceValidationException("治理问题已变化，请刷新计划后重试");
        }
    }

    private Map<Long, GovernancePlan> indexPlansByIssue(List<GovernancePlan> plans) {
        var result = new HashMap<Long, GovernancePlan>();
        plans.forEach(plan -> plan.issueIds().forEach(issueId -> result.put(issueId, plan)));
        return result;
    }

    private GovernanceTask startedTask(GovernanceTask task, long scopeSnapshotId, long ruleSnapshotId) {
        return new GovernanceTask(
                task.id(), task.taskNumber(), task.name(), task.actionType(), task.issueType(),
                task.ownerUserId(), task.ownerName(), task.assigneeId(), task.dueDate(),
                task.status().moveTo(GovernanceTaskStatus.IN_PROGRESS), task.currentRound(),
                task.workflowVersion(), scopeSnapshotId, ruleSnapshotId,
                task.legacyTotal(), task.legacyCompleted(), task.version());
    }
}
