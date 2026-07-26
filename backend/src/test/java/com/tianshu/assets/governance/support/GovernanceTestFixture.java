package com.tianshu.assets.governance.support;

import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceEmployeeDirectory;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceIssueStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceRuleCatalog;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceTaskStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceWorkflowStore;
import com.tianshu.assets.governance.issue.application.GovernanceIssueService;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import com.tianshu.assets.governance.issue.domain.GovernanceIssue;
import com.tianshu.assets.governance.issue.domain.GovernanceIssueStatus;
import com.tianshu.assets.governance.task.application.GovernanceTaskApplicationService;
import com.tianshu.assets.governance.task.application.GovernanceTaskApplicationService.CreatePlanCommand;
import com.tianshu.assets.governance.task.application.GovernanceTaskStartService;
import com.tianshu.assets.governance.task.domain.GovernancePlan;
import com.tianshu.assets.governance.task.domain.GovernanceRuleSnapshot;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class GovernanceTestFixture {

    private final InMemoryGovernanceTaskStore taskStore;
    private final InMemoryGovernanceIssueStore issueStore;
    private final InMemoryGovernanceWorkflowStore workflowStore;
    private final GovernanceIssueService issueService;
    private final GovernanceTaskApplicationService taskService;
    private final GovernanceTaskStartService startService;
    private GovernanceTask draft;

    private GovernanceTestFixture() {
        taskStore = new InMemoryGovernanceTaskStore();
        issueStore = new InMemoryGovernanceIssueStore();
        workflowStore = new InMemoryGovernanceWorkflowStore();
        var ruleCatalog = new InMemoryGovernanceRuleCatalog(new GovernanceRuleSnapshot(
                0, "FIELD-COMPLETENESS", 3, 3, Map.of("specialty", 5L), "FIELD-QUALITY", 2));
        issueService = new GovernanceIssueService(issueStore, taskStore);
        taskService = new GovernanceTaskApplicationService(
                taskStore, new InMemoryGovernanceEmployeeDirectory(), issueStore);
        startService = new GovernanceTaskStartService(taskStore, issueStore, workflowStore, ruleCatalog);
        issueStore.insertAll(List.of(
                issue(1001, 101, GovernanceField.DESCRIPTION, "/description", "\"\"", 7),
                issue(1002, 102, GovernanceField.SPECIALTIES, "/specialties", "[]", 9)));
    }

    public static GovernanceTestFixture fieldClosure() {
        return new GovernanceTestFixture();
    }

    public GovernanceTask closedLoopDraftWithTwoIssues() {
        if (draft == null) {
            draft = issueService.createTask(new GovernanceIssueService.CreateGovernanceTaskCommand(
                    "字段治理范围固化", List.of(1001L, 1002L), "emp-chen", "陈工",
                    LocalDate.of(2026, 9, 30)));
        }
        return taskStore.findById(draft.id()).orElseThrow();
    }

    public GovernanceTask validDraft() {
        var task = closedLoopDraftWithTwoIssues();
        if (taskStore.findPlans(task.id()).isEmpty()) {
            taskService.createPlan(task.id(), new CreatePlanCommand(
                    0, "字段治理计划", LocalDate.of(2026, 9, 1),
                    LocalDate.of(2026, 9, 15), "emp-chen", List.of(),
                    List.of(1001L, 1002L)));
        }
        return taskStore.findById(task.id()).orElseThrow();
    }

    public GovernancePlan addPlan(
            long taskId, long planId, List<Long> issueIds, List<Long> dependencyIds) {
        return taskStore.insertPlan(GovernancePlan.closedLoop(
                planId, taskId, 0, "治理计划 " + planId, "emp-chen",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 15),
                dependencyIds, issueIds, 0));
    }

    public GovernanceTaskStartService startService() {
        return startService;
    }

    public InMemoryGovernanceWorkflowStore workflowStore() {
        return workflowStore;
    }

    public InMemoryGovernanceTaskStore taskStore() {
        return taskStore;
    }

    public InMemoryGovernanceIssueStore issueStore() {
        return issueStore;
    }

    public GovernanceTaskApplicationService taskService() {
        return taskService;
    }

    private GovernanceIssue issue(
            long id, long assetId, GovernanceField field, String path, String originalFactJson,
            long assetVersion) {
        return new GovernanceIssue(
                id, assetId, field, "MISSING_" + field.name(), path, "FIELD_REQUIRED", 3,
                originalFactJson, assetVersion, "asset:" + assetId + ":v" + assetVersion,
                "HIGH", true, GovernanceIssueStatus.OPEN, null, 0);
    }
}
