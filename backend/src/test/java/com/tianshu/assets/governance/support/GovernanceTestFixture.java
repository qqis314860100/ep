package com.tianshu.assets.governance.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import com.tianshu.assets.governance.acceptance.application.GovernanceQualityService;
import com.tianshu.assets.governance.acceptance.application.GovernanceAcceptanceService;
import com.tianshu.assets.governance.acceptance.application.GovernanceApplicationJobService;
import com.tianshu.assets.governance.acceptance.domain.GovernanceOperationJob;
import com.tianshu.assets.governance.acceptance.application.GovernanceQualityService.QualityFact;
import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceRound;
import com.tianshu.assets.governance.acceptance.domain.GovernanceQualityMetric;
import com.tianshu.assets.governance.acceptance.domain.GovernanceQualityPolicySnapshot;
import com.tianshu.assets.dictionary.infrastructure.InMemoryDictionaryStore;
import com.tianshu.assets.governance.execution.application.FieldSupplementActionHandler;
import com.tianshu.assets.governance.confirmation.application.GovernanceConfirmationService;
import com.tianshu.assets.governance.confirmation.application.GovernanceConfirmationService.DecisionCommand;
import com.tianshu.assets.governance.confirmation.domain.GovernanceConfirmationDecision.Decision;
import com.tianshu.assets.governance.confirmation.domain.GovernanceConfirmationRound;
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
import com.tianshu.assets.governance.infrastructure.InMemoryAssetResponsibilityAdapter;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceConfirmationStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceAcceptanceStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceAssetAdapter;
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
import com.tianshu.assets.governance.task.application.GovernanceReworkService;
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
    private final InMemoryGovernanceConfirmationStore confirmationStore;
    private final InMemoryGovernanceAcceptanceStore acceptanceStore;
    private final InMemoryAssetResponsibilityAdapter responsibilityAdapter;
    private final GovernanceConfirmationService confirmationService;
    private final GovernanceQualityService qualityService;
    private final GovernanceAcceptanceService acceptanceService;
    private final GovernanceReworkService reworkService;
    private final InMemoryGovernanceAssetAdapter governanceAssetAdapter;
    private final GovernanceApplicationJobService applicationJobService;
    private GovernanceTask draft;
    private GovernanceOperationJob acceptedJob;
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
        confirmationStore = new InMemoryGovernanceConfirmationStore();
        acceptanceStore = new InMemoryGovernanceAcceptanceStore();
        responsibilityAdapter = new InMemoryAssetResponsibilityAdapter();
        var ruleCatalog = new InMemoryGovernanceRuleCatalog(new GovernanceRuleSnapshot(
                0, "FIELD-COMPLETENESS", 3, 3,
                Map.of("specialty", 5L, "scope", 8L), "FIELD-QUALITY", 2));
        issueService = new GovernanceIssueService(issueStore, taskStore);
        taskService = new GovernanceTaskApplicationService(
                taskStore, new InMemoryGovernanceEmployeeDirectory(), issueStore,
                workflowStore, executionStore, confirmationStore);
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
        responsibilityAdapter.assign(101, "owner-1", "scope-owner-a");
        responsibilityAdapter.assign(102, "owner-1", batch ? "scope-owner-a" : "scope-owner-b");
        responsibilityAdapter.assign(103, "owner-1", "scope-owner-a");
        responsibilityAdapter.assign(104, "owner-1", "scope-owner-a");
        confirmationService = new GovernanceConfirmationService(
                confirmationStore, executionStore, taskStore, responsibilityAdapter,
                Clock.fixed(Instant.parse("2026-07-26T07:00:00Z"), ZoneOffset.UTC));
        qualityService = new GovernanceQualityService(
                acceptanceStore,
                Clock.fixed(Instant.parse("2026-07-26T08:00:00Z"), ZoneOffset.UTC));
        acceptanceService = new GovernanceAcceptanceService(
                acceptanceStore, executionStore, taskStore,
                Clock.fixed(Instant.parse("2026-07-26T09:00:00Z"), ZoneOffset.UTC));
        reworkService = new GovernanceReworkService(
                taskStore, executionStore,
                Clock.fixed(Instant.parse("2026-07-26T10:00:00Z"), ZoneOffset.UTC));
        governanceAssetAdapter = new InMemoryGovernanceAssetAdapter();
        applicationJobService = new GovernanceApplicationJobService(
                acceptanceStore, executionStore, issueStore, taskStore, governanceAssetAdapter);
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

    public GovernanceConfirmationRound pendingConfirmationWithTwoItems() {
        var task = validStartedTask();
        executionService().items(task.id()).stream()
                .map(GovernanceExecutionService.ItemExecutionContext::item)
                .forEach(item -> markSubmitted(item.id()));
        task = taskStore.findById(task.id()).orElseThrow();
        taskService.submitForConfirmation(task.id(), task.version());
        return confirmationService.current(task.id()).round();
    }

    public GovernanceConfirmationService confirmationService() {
        return confirmationService;
    }

    public GovernanceQualityService qualityService() {
        return qualityService;
    }

    public GovernanceAcceptanceService acceptanceService() {
        return acceptanceService;
    }

    public GovernanceReworkService reworkService() {
        return reworkService;
    }

    public GovernanceApplicationJobService applicationJobService() {
        return applicationJobService;
    }

    public InMemoryGovernanceAssetAdapter assetPort() {
        return governanceAssetAdapter;
    }

    public GovernanceOperationJob acceptedTaskWithTwoItems() {
        if (acceptedJob != null) return acceptedJob;
        var round = pendingAcceptance();
        passAllSamples(round.id());
        round = acceptanceService.current(round.taskId());
        var completion = acceptanceService.complete(round.taskId(), round.id(), round.version(), "qa-1");
        var job = acceptanceStore.applicationJob(completion.applicationJobId()).orElseThrow();
        executionStore.items(job.taskId()).forEach(item ->
                governanceAssetAdapter.seed(item.assetId(), item.assetVersion()));
        acceptedJob = job;
        return job;
    }

    public GovernanceOperationJob acceptedJobFor(long assetId) {
        var job = acceptedTaskWithTwoItems();
        if (job.items().stream().map(item -> executionStore.item(item.itemId()).assetId())
                .noneMatch(id -> id == assetId)) {
            throw new IllegalArgumentException("验收作业不包含资产：" + assetId);
        }
        return job;
    }

    public void addOpenBlockingIssue(long assetId, String issueType) {
        var assetVersion = governanceAssetAdapter.snapshot(assetId).version();
        issueStore.insertAll(List.of(new GovernanceIssue(
                0, assetId, GovernanceField.DESCRIPTION, issueType, "/files/primary",
                "CONTINUOUS_GOVERNANCE", 1, "null", assetVersion,
                "asset:" + assetId + ":blocking", "HIGH", true,
                GovernanceIssueStatus.OPEN, null, 0)));
    }

    public InMemoryGovernanceAcceptanceStore acceptanceStore() {
        return acceptanceStore;
    }

    public GovernanceQualityPolicySnapshot policyAllowingNotApplicable() {
        return new GovernanceQualityPolicySnapshot(
                1, "FIELD-QUALITY", 2,
                java.util.Arrays.stream(GovernanceQualityMetric.values()).collect(
                        java.util.stream.Collectors.toMap(metric -> metric, metric -> 0.8)),
                true, true, 1);
    }

    public GovernanceAcceptanceRound openAcceptanceRound() {
        var existing = acceptanceStore.currentRound(validStartedTask().id());
        if (existing.isPresent()) return existing.orElseThrow();
        var confirmationRound = pendingConfirmationWithTwoItems();
        var confirmationItems = confirmationService.current(confirmationRound.taskId()).items();
        confirmationItems.forEach(item -> confirmationService.decide(
                confirmationRound.id(), item.itemId(),
                new DecisionCommand(Decision.APPROVED, "", 0, item.responsibleUserId())));
        confirmationService.complete(
                confirmationRound.taskId(), confirmationRound.id(), confirmationRound.version());
        var task = taskStore.findById(confirmationRound.taskId()).orElseThrow();
        var facts = executionStore.items(task.id()).stream().map(item -> {
            var asset = assetRepository.findById(item.assetId()).orElseThrow();
            var scopeValid = asset.scopes().stream().anyMatch(scope ->
                    !scope.platformFamily().isBlank() && !scope.productLine().isBlank()
                            && !scope.base().isBlank() && !scope.productionLine().isBlank());
            return new QualityFact(
                    item.id(), executionStore.currentResult(item.id()) != null, scopeValid,
                    item.targetField() == GovernanceField.SPECIALTIES ? Boolean.TRUE : null,
                    responsibilityAdapter.currentResponsibility(item.assetId()).isPresent());
        }).toList();
        return qualityService.openRound(
                task.id(), task.currentRound(), policyAllowingNotApplicable(), facts);
    }

    public GovernanceAcceptanceRound pendingAcceptance() {
        return openAcceptanceRound();
    }

    public GovernanceAcceptanceRound failMetric(
            long roundId, GovernanceQualityMetric metric, List<Long> itemIds) {
        var round = acceptanceStore.round(roundId);
        var metrics = round.metricResults().stream().map(result ->
                result.metric() == metric
                        ? new com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceMetricResult(
                                result.id(), result.roundId(), result.metric(),
                                Math.max(0, result.denominator() - itemIds.size()), result.denominator(),
                                result.denominator() == 0 ? null
                                        : (double) Math.max(0, result.denominator() - itemIds.size())
                                                / result.denominator(),
                                result.threshold(), result.applicability(), false, itemIds,
                                result.version() + 1)
                        : result).toList();
        return acceptanceStore.updateRound(new GovernanceAcceptanceRound(
                round.id(), round.taskId(), round.governanceRound(), round.policy(), metrics, round.samples(),
                round.status(), round.createdAt(), round.completedAt(), round.version()), round.version());
    }

    public void passAllSamples(long roundId) {
        var round = acceptanceStore.round(roundId);
        round.samples().forEach(sample -> qualityService.saveSample(
                roundId, sample.itemId(), true, "", "qa-1", sample.version()));
    }

    public GovernanceTask reworkRequiredTask() {
        var round = pendingAcceptance();
        var affected = executionStore.items(round.taskId()).get(1).id();
        passAllSamples(round.id());
        round = failMetric(round.id(), GovernanceQualityMetric.OWNER_COVERAGE, List.of(affected));
        acceptanceService.complete(round.taskId(), round.id(), round.version(), "qa-1");
        return taskStore.findById(round.taskId()).orElseThrow();
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
        var item = executionStore().item(itemId);
        var proposedValue = switch (item.targetField()) {
            case DESCRIPTION -> "{\"description\":\"补充后的功能说明\"}";
            case SPECIALTIES -> "{\"specialtyItemIds\":[201]}";
            case OWNER -> "{\"ownerUserId\":\"emp-chen\",\"ownerName\":\"陈工\"}";
            case SCOPE -> mixedScopeJson();
        };
        var result = executionService().saveDraft(itemId, new SaveResultDraftCommand(
                item.version(), item.assetVersion(), proposedValue, "emp-chen"));
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
