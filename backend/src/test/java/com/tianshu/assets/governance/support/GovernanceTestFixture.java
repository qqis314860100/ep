package com.tianshu.assets.governance.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import com.tianshu.assets.dictionary.infrastructure.InMemoryDictionaryStore;
import com.tianshu.assets.governance.execution.application.FieldSupplementActionHandler;
import com.tianshu.assets.governance.execution.application.GovernanceActionHandler.ValidationContext;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionService;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionService.SaveResultDraftCommand;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionService.BatchResultCommand;
import com.tianshu.assets.governance.execution.domain.GovernanceItem;
import com.tianshu.assets.governance.execution.domain.GovernanceItemStatus;
import com.tianshu.assets.governance.execution.domain.GovernanceResultVersion;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceEmployeeDirectory;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceExecutionStore;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    private InMemoryGovernanceExecutionStore executionStore;
    private InMemoryAssetRepository assetRepository;
    private GovernanceExecutionService executionService;

    private GovernanceTestFixture() {
        this(false);
    }

    private GovernanceTestFixture(boolean batch) {
        taskStore = new InMemoryGovernanceTaskStore();
        issueStore = new InMemoryGovernanceIssueStore();
        workflowStore = new InMemoryGovernanceWorkflowStore();
        executionStore = new InMemoryGovernanceExecutionStore(workflowStore);
        var ruleCatalog = new InMemoryGovernanceRuleCatalog(new GovernanceRuleSnapshot(
                0, "FIELD-COMPLETENESS", 3, 3,
                Map.of("specialty", 5L, "scope", 8L), "FIELD-QUALITY", 2));
        issueService = new GovernanceIssueService(issueStore, taskStore);
        taskService = new GovernanceTaskApplicationService(
                taskStore, new InMemoryGovernanceEmployeeDirectory(), issueStore,
                workflowStore, executionStore);
        startService = new GovernanceTaskStartService(taskStore, issueStore, workflowStore, ruleCatalog);
        issueStore.insertAll(batch
                ? List.of(
                        issue(1001, 101, GovernanceField.DESCRIPTION, "/description", "\"\"", 7, "scope-a"),
                        issue(1002, 102, GovernanceField.DESCRIPTION, "/description", "\"\"", 9, "scope-a"),
                        issue(1003, 103, GovernanceField.DESCRIPTION, "/description", "\"\"", 2, "scope-a"),
                        issue(1004, 104, GovernanceField.DESCRIPTION, "/description", "\"\"", 4, "scope-a"))
                : List.of(
                        issue(1001, 101, GovernanceField.DESCRIPTION, "/description", "\"\"", 7),
                        issue(1002, 102, GovernanceField.SPECIALTIES, "/specialties", "[]", 9)));
    }

    public static GovernanceTestFixture fieldClosure() {
        return new GovernanceTestFixture();
    }

    public static GovernanceTestFixture batchFieldClosure() {
        return new GovernanceTestFixture(true);
    }

    public GovernanceTask closedLoopDraftWithTwoIssues() {
        if (draft == null) {
            var issueIds = issueStore.find(null, null, null).stream().map(GovernanceIssue::id).toList();
            draft = issueService.createTask(new GovernanceIssueService.CreateGovernanceTaskCommand(
                    "字段治理范围固化", issueIds, "emp-chen", "陈工",
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
                    issueStore.findClaimedByTask(task.id()).stream().map(GovernanceIssue::id).toList()));
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

    public GovernanceItem descriptionItem() {
        prepareExecution();
        return executionService.items(draft.id()).stream()
                .map(GovernanceExecutionService.ItemExecutionContext::item)
                .filter(item -> item.targetField() == GovernanceField.DESCRIPTION)
                .findFirst().orElseThrow();
    }

    public GovernanceExecutionService executionService() {
        prepareExecution();
        return executionService;
    }

    public GovernanceTask validStartedTask() {
        prepareExecution();
        return taskStore.findById(draft.id()).orElseThrow();
    }

    public List<GovernanceItem> batchItems() {
        return executionService().items(validStartedTask().id()).stream()
                .map(GovernanceExecutionService.ItemExecutionContext::item).toList();
    }

    public BatchResultCommand validDescriptionCommand(long itemId) {
        return descriptionCommand(itemId, "补充后的功能说明");
    }

    public BatchResultCommand blankDescriptionCommand(long itemId) {
        return descriptionCommand(itemId, " ");
    }

    public BatchResultCommand staleVersionCommand(long itemId) {
        var item = executionStore().item(itemId);
        return new BatchResultCommand(itemId, item.version() + 1, item.assetVersion(), item.targetField(),
                3, item.scopeFingerprint(), "{\"description\":\"补充后的功能说明\"}", "emp-chen");
    }

    public BatchResultCommand descriptionCommand(long itemId, String description) {
        var item = executionStore().item(itemId);
        return new BatchResultCommand(itemId, item.version(), item.assetVersion(), item.targetField(),
                3, item.scopeFingerprint(), "{\"description\":\"" + description + "\"}", "emp-chen");
    }

    public BatchResultCommand withTargetField(BatchResultCommand source, long itemId) {
        var item = executionStore().item(itemId);
        return new BatchResultCommand(itemId, item.version(), item.assetVersion(), GovernanceField.SPECIALTIES,
                source.standardVersion(), source.scopeFingerprint(), source.proposedValueJson(), source.actorUserId());
    }

    public BatchResultCommand withStandardVersion(BatchResultCommand source, long itemId) {
        var item = executionStore().item(itemId);
        return new BatchResultCommand(itemId, item.version(), item.assetVersion(), source.targetField(),
                source.standardVersion() + 1, source.scopeFingerprint(), source.proposedValueJson(), source.actorUserId());
    }

    public BatchResultCommand withScopeFingerprint(BatchResultCommand source, long itemId) {
        var item = executionStore().item(itemId);
        return new BatchResultCommand(itemId, item.version(), item.assetVersion(), source.targetField(),
                source.standardVersion(), "other-scope", source.proposedValueJson(), source.actorUserId());
    }

    public void markSubmitted(long itemId) {
        var result = executionService().saveDraft(itemId, validDescriptionCommand(itemId).toSaveCommand());
        executionService().submit(itemId, result.id(), result.version(), "emp-chen");
    }

    public void markBlocked(long itemId, String reason) {
        executionStore().updateItemStatus(itemId, GovernanceItemStatus.BLOCKED, reason);
    }

    public InMemoryGovernanceExecutionStore executionStore() {
        prepareExecution();
        return executionStore;
    }

    public InMemoryAssetRepository assetRepository() {
        prepareExecution();
        return assetRepository;
    }

    public ValidationContext scopeContext() {
        var rules = new GovernanceRuleSnapshot(
                0, "FIELD-COMPLETENESS", 3, 3,
                Map.of("specialty", 5L, "scope", 8L), "FIELD-QUALITY", 2);
        return new ValidationContext(rules, rules, List.of(
                new AssetScope("乘用车", "H03", "宁德基地", "A 拉线", "焊接段", "乘用车", "底部水冷"),
                new AssetScope("商用车", "P02", "溧阳基地", "B 拉线", "PACK 段", "商用车", "商用车")));
    }

    public String mixedScopeJson() {
        return "{\"scopes\":[{\"platformFamily\":\"乘用车\",\"platformVariant\":\"底部水冷\","
                + "\"productLine\":\"H03\",\"base\":\"溧阳基地\","
                + "\"productionLine\":\"B 拉线\",\"processSection\":\"PACK 段\"}]}";
    }

    public SaveResultDraftCommand commandFor(GovernanceResultVersion result) {
        var item = executionStore().item(result.itemId());
        return new SaveResultDraftCommand(
                item.version(), item.assetVersion(), result.proposedValueJson(), "emp-chen");
    }

    private void prepareExecution() {
        if (executionService != null) return;
        var task = validDraft();
        if (task.status() == com.tianshu.assets.governance.task.domain.GovernanceTaskStatus.DRAFT) {
            startService.start(task.id(), task.version(), "emp-admin");
        }
        assetRepository = new InMemoryAssetRepository();
        var ruleCatalog = new InMemoryGovernanceRuleCatalog(new GovernanceRuleSnapshot(
                0, "FIELD-COMPLETENESS", 3, 3,
                Map.of("specialty", 5L, "scope", 8L), "FIELD-QUALITY", 2));
        executionService = new GovernanceExecutionService(
                executionStore, workflowStore, ruleCatalog, assetRepository,
                new FieldSupplementActionHandler(
                        new ObjectMapper(), new InMemoryDictionaryStore(),
                        new InMemoryGovernanceEmployeeDirectory()),
                Clock.fixed(Instant.parse("2026-07-26T06:00:00Z"), ZoneOffset.UTC));
    }

    private GovernanceIssue issue(
            long id, long assetId, GovernanceField field, String path, String originalFactJson,
            long assetVersion) {
        return issue(id, assetId, field, path, originalFactJson, assetVersion,
                "asset:" + assetId + ":v" + assetVersion);
    }

    private GovernanceIssue issue(
            long id, long assetId, GovernanceField field, String path, String originalFactJson,
            long assetVersion, String scopeFingerprint) {
        return new GovernanceIssue(
                id, assetId, field, "MISSING_" + field.name(), path, "FIELD_REQUIRED", 3,
                originalFactJson, assetVersion, scopeFingerprint,
                "HIGH", true, GovernanceIssueStatus.OPEN, null, 0);
    }
}
