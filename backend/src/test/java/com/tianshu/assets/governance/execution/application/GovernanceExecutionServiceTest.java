package com.tianshu.assets.governance.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import com.tianshu.assets.dictionary.domain.DictionaryItem;
import com.tianshu.assets.dictionary.domain.DictionaryStatus;
import com.tianshu.assets.dictionary.infrastructure.InMemoryDictionaryStore;
import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.application.GovernanceValidationException;
import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import com.tianshu.assets.governance.execution.application.GovernanceActionHandler.ValidationContext;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionService.SaveResultDraftCommand;
import com.tianshu.assets.governance.execution.domain.GovernanceItem;
import com.tianshu.assets.governance.execution.domain.GovernanceItemStatus;
import com.tianshu.assets.governance.execution.domain.GovernanceResultStatus;
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
import com.tianshu.assets.governance.support.GovernanceTestFixture;
import com.tianshu.assets.governance.task.application.GovernanceTaskApplicationService;
import com.tianshu.assets.governance.task.application.GovernanceTaskStartService;
import com.tianshu.assets.governance.task.domain.GovernanceRuleSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GovernanceExecutionServiceTest {

    private static final GovernanceRuleSnapshot RULES = new GovernanceRuleSnapshot(
            0, "FIELD-COMPLETENESS", 3, 3,
            Map.of("specialty", 5L, "scope", 8L), "FIELD-QUALITY", 2);

    private GovernanceTestFixture fixture;
    private InMemoryGovernanceExecutionStore executionStore;
    private InMemoryAssetRepository assetRepository;
    private GovernanceExecutionService service;
    private GovernanceItem descriptionItem;

    @BeforeEach
    void setUp() {
        fixture = GovernanceTestFixture.fieldClosure();
        executionStore = fixture.executionStore();
        assetRepository = fixture.assetRepository();
        service = fixture.executionService();
        descriptionItem = fixture.descriptionItem();
    }

    @Test
    void savesDraftWithoutChangingOfficialAssetAndSubmitsImmutableVersion() {
        var officialBefore = assetRepository.findById(descriptionItem.assetId()).orElseThrow().description();

        var draft = service.saveDraft(descriptionItem.id(), new SaveResultDraftCommand(
                descriptionItem.version(), descriptionItem.assetVersion(),
                "{\"description\":\"焊接工位设备总成\"}", "emp-chen"));
        var submitted = service.submit(descriptionItem.id(), draft.id(), draft.version(), "emp-chen");

        assertThat(submitted.status()).isEqualTo(GovernanceResultStatus.SUBMITTED);
        assertThat(executionStore.item(descriptionItem.id()).status()).isEqualTo(GovernanceItemStatus.SUBMITTED);
        assertThat(assetRepository.findById(descriptionItem.assetId()).orElseThrow().description())
                .isEqualTo(officialBefore);
        assertThatThrownBy(() -> service.saveDraft(
                descriptionItem.id(), fixture.commandFor(submitted)))
                .isInstanceOf(GovernanceConflictException.class)
                .hasMessage("已提交结果不可原地修改");
    }

    @Test
    void repeatedlySavesSameDraftWithOptimisticItemVersion() {
        var first = service.saveDraft(descriptionItem.id(), new SaveResultDraftCommand(
                0, descriptionItem.assetVersion(), "{\"description\":\"第一版说明\"}", "emp-chen"));
        var itemAfterFirstSave = executionStore.item(descriptionItem.id());

        var second = service.saveDraft(descriptionItem.id(), new SaveResultDraftCommand(
                itemAfterFirstSave.version(), itemAfterFirstSave.assetVersion(),
                "{\"description\":\"第二版说明\"}", "emp-chen"));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.resultVersion()).isEqualTo(first.resultVersion());
        assertThat(second.version()).isEqualTo(first.version() + 1);
        assertThat(second.proposedValueJson()).contains("第二版说明");
        assertThatThrownBy(() -> service.saveDraft(descriptionItem.id(), new SaveResultDraftCommand(
                0, descriptionItem.assetVersion(), "{\"description\":\"覆盖\"}", "emp-chen")))
                .isInstanceOf(GovernanceConflictException.class)
                .hasMessageContaining("治理项已变化");
    }

    @Test
    void rejectsStaleAssetAndResultVersions() {
        assertThatThrownBy(() -> service.saveDraft(descriptionItem.id(), new SaveResultDraftCommand(
                descriptionItem.version(), descriptionItem.assetVersion() + 1,
                "{\"description\":\"说明\"}", "emp-chen")))
                .isInstanceOf(GovernanceConflictException.class)
                .hasMessageContaining("资产版本已变化");

        var draft = service.saveDraft(descriptionItem.id(), new SaveResultDraftCommand(
                descriptionItem.version(), descriptionItem.assetVersion(),
                "{\"description\":\"说明\"}", "emp-chen"));
        assertThatThrownBy(() -> service.submit(
                descriptionItem.id(), draft.id(), draft.version() + 1, "emp-chen"))
                .isInstanceOf(GovernanceConflictException.class)
                .hasMessageContaining("结果版本已变化");
        assertThat(executionStore.item(descriptionItem.id()).status()).isEqualTo(GovernanceItemStatus.PROCESSING);
    }

    @Test
    void returnsOriginalFactRuleContextAndCurrentResultForTaskItems() {
        var draft = service.saveDraft(descriptionItem.id(), new SaveResultDraftCommand(
                descriptionItem.version(), descriptionItem.assetVersion(),
                "{\"description\":\"执行上下文说明\"}", "emp-chen"));

        var context = service.items(descriptionItem.taskId()).stream()
                .filter(candidate -> candidate.item().id() == descriptionItem.id())
                .findFirst().orElseThrow();

        assertThat(context.originalFactJson()).isEqualTo("\"\"");
        assertThat(context.ruleSnapshot().dataStandardVersion()).isEqualTo(3);
        assertThat(context.currentResult()).isEqualTo(draft);
        assertThat(context.item().version()).isEqualTo(1);
        assertThat(context.blockReason()).isNull();
        assertThat(context.reworkSourceItemId()).isNull();
    }

    @Test
    void validatesDescriptionSpecialtyOwnerAndScopeWithStructuredJson() {
        var dictionaryStore = new InMemoryDictionaryStore();
        var handler = new FieldSupplementActionHandler(
                new ObjectMapper(), dictionaryStore, new InMemoryGovernanceEmployeeDirectory());
        var context = fixture.scopeContext();

        handler.validate(GovernanceField.DESCRIPTION,
                "{\"description\":\"焊接工位设备总成\"}", context);
        handler.validate(GovernanceField.SPECIALTIES,
                "{\"specialtyItemIds\":[201,202]}", context);
        handler.validate(GovernanceField.OWNER,
                "{\"ownerUserId\":\"emp-chen\",\"ownerName\":\"陈工\"}", context);
        handler.validate(GovernanceField.SCOPE, validScopeJson(), context);

        assertThatThrownBy(() -> handler.validate(
                GovernanceField.DESCRIPTION, "{\"description\":\"  \"}", context))
                .isInstanceOf(GovernanceValidationException.class)
                .hasMessage("说明不能为空");
        assertThatThrownBy(() -> handler.validate(
                GovernanceField.OWNER,
                "{\"ownerUserId\":\"emp-missing\",\"ownerName\":\"不存在\"}", context))
                .isInstanceOf(GovernanceValidationException.class)
                .hasMessage("负责人必须来自员工目录");
        assertThatThrownBy(() -> handler.validate(
                GovernanceField.SCOPE, fixture.mixedScopeJson(), context))
                .isInstanceOf(GovernanceValidationException.class)
                .hasMessage("产品与生产条件必须来自同一个适用范围");
        assertThatThrownBy(() -> handler.validate(
                GovernanceField.SCOPE,
                "{\"scopes\":[{\"platformFamily\":\"乘用车\",\"platformVariant\":\"底部水冷\"}]}",
                context))
                .isInstanceOf(GovernanceValidationException.class)
                .hasMessage("适用范围层级必须完整");
    }

    @Test
    void rejectsDisabledSpecialtyAndChangedDictionaryVersion() {
        var dictionaryStore = new InMemoryDictionaryStore();
        var current = dictionaryStore.findById(201).orElseThrow();
        dictionaryStore.update(copyWithStatus(current, DictionaryStatus.DISABLED), current.version());
        var handler = new FieldSupplementActionHandler(
                new ObjectMapper(), dictionaryStore, new InMemoryGovernanceEmployeeDirectory());

        assertThatThrownBy(() -> handler.validate(
                GovernanceField.SPECIALTIES, "{\"specialtyItemIds\":[201]}", fixture.scopeContext()))
                .isInstanceOf(GovernanceValidationException.class)
                .hasMessage("专业类别必须选择启用的字典项");

        var changedRules = new GovernanceRuleSnapshot(
                0, "FIELD-COMPLETENESS", 3, 3,
                Map.of("specialty", 6L, "scope", 8L), "FIELD-QUALITY", 2);
        assertThatThrownBy(() -> handler.validate(
                GovernanceField.SPECIALTIES, "{\"specialtyItemIds\":[202]}",
                new ValidationContext(RULES, changedRules, fixture.scopeContext().validScopes())))
                .isInstanceOf(GovernanceConflictException.class)
                .hasMessage("专业字典版本已变化，请刷新后重试");
    }

    @Test
    void rejectsMalformedOrUnexpectedJsonShape() {
        var handler = new FieldSupplementActionHandler(
                new ObjectMapper(), new InMemoryDictionaryStore(),
                new InMemoryGovernanceEmployeeDirectory());

        assertThatThrownBy(() -> handler.validate(
                GovernanceField.DESCRIPTION, "not-json", fixture.scopeContext()))
                .isInstanceOf(GovernanceValidationException.class)
                .hasMessage("治理结果 JSON 格式不正确");
        assertThatThrownBy(() -> handler.validate(
                GovernanceField.DESCRIPTION,
                "{\"description\":\"说明\",\"ownerUserId\":\"emp-chen\"}", fixture.scopeContext()))
                .isInstanceOf(GovernanceValidationException.class)
                .hasMessage("治理结果结构不正确");
        assertThatThrownBy(() -> handler.validate(
                GovernanceField.SPECIALTIES,
                "{\"specialtyItemIds\":[201.5]}", fixture.scopeContext()))
                .isInstanceOf(GovernanceValidationException.class)
                .hasMessage("治理结果结构不正确");
    }

    @Test
    void acceptsScopeWithOptionalEmptyProcessSectionWhenWholeScopeMatches() {
        var handler = new FieldSupplementActionHandler(
                new ObjectMapper(), new InMemoryDictionaryStore(),
                new InMemoryGovernanceEmployeeDirectory());
        var rules = fixture.scopeContext().frozenRules();
        var context = new ValidationContext(rules, rules, List.of(
                new AssetScope("乘用车", "H03", "宁德基地", "A 拉线", "", "乘用车", "底部水冷")));

        handler.validate(GovernanceField.SCOPE,
                "{\"scopes\":[{\"platformFamily\":\"乘用车\",\"platformVariant\":\"底部水冷\","
                        + "\"productLine\":\"H03\",\"base\":\"宁德基地\","
                        + "\"productionLine\":\"A 拉线\",\"processSection\":\"\"}]}",
                context);

        assertThatThrownBy(() -> handler.validate(GovernanceField.SCOPE,
                "{\"scopes\":[{\"platformFamily\":\"乘用车\",\"platformVariant\":\"底部水冷\","
                        + "\"productLine\":\"H03\",\"base\":\"宁德基地\","
                        + "\"productionLine\":\"A 拉线\",\"processSection\":123}]}",
                context))
                .isInstanceOf(GovernanceValidationException.class)
                .hasMessage("治理结果结构不正确");
    }

    @Test
    void savesControlledScopeCandidateMissingFromAssetsCurrentScopes() {
        var scenario = scopeScenario(new InMemoryGovernanceRuleCatalog(RULES));

        var draft = scenario.service().saveDraft(scenario.item().id(), new SaveResultDraftCommand(
                scenario.item().version(), scenario.item().assetVersion(), validScopeJson(), "emp-chen"));

        assertThat(draft.status()).isEqualTo(GovernanceResultStatus.DRAFT);
        assertThat(scenario.assetRepository().findById(scenario.item().assetId()).orElseThrow().scopes())
                .noneMatch(scope -> scope.platformVariant().equals("底部水冷")
                        && scope.base().equals("宁德基地")
                        && scope.productionLine().equals("A 拉线"));
    }

    @Test
    void rejectsChangedScopeDictionaryVersionBeforeValidatingCandidate() {
        var changedRules = new GovernanceRuleSnapshot(
                0, "FIELD-COMPLETENESS", 3, 3,
                Map.of("specialty", 5L, "scope", 9L), "FIELD-QUALITY", 2);
        var scenario = scopeScenario(new InMemoryGovernanceRuleCatalog(changedRules));

        assertThatThrownBy(() -> scenario.service().saveDraft(
                scenario.item().id(), new SaveResultDraftCommand(
                        scenario.item().version(), scenario.item().assetVersion(),
                        validScopeJson(), "emp-chen")))
                .isInstanceOf(GovernanceVersionConflictException.class)
                .hasMessage("适用范围字典版本已变化，请刷新后重试");
    }

    private String validScopeJson() {
        return "{\"scopes\":[{\"platformFamily\":\"乘用车\",\"platformVariant\":\"底部水冷\","
                + "\"productLine\":\"H03\",\"base\":\"宁德基地\","
                + "\"productionLine\":\"A 拉线\",\"processSection\":\"焊接段\"}]}";
    }

    private ScopeScenario scopeScenario(InMemoryGovernanceRuleCatalog enabledCatalog) {
        var issueStore = new InMemoryGovernanceIssueStore();
        issueStore.insertAll(List.of(new GovernanceIssue(
                3001, 104, GovernanceField.SCOPE, "MISSING_SCOPE", "/scopes",
                "FIELD_REQUIRED", 3, "[]", 4, "asset:104:v4", "HIGH", true,
                GovernanceIssueStatus.OPEN, null, 0,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"))));
        var taskStore = new InMemoryGovernanceTaskStore();
        var workflowStore = new InMemoryGovernanceWorkflowStore();
        var issueService = new GovernanceIssueService(issueStore, taskStore);
        var taskService = new GovernanceTaskApplicationService(
                taskStore, new InMemoryGovernanceEmployeeDirectory(), issueStore);
        var task = issueService.createTask(new GovernanceIssueService.CreateGovernanceTaskCommand(
                "适用范围补充", List.of(3001L), "emp-chen", "陈工", LocalDate.of(2026, 9, 30)));
        taskService.createPlan(task.id(), new GovernanceTaskApplicationService.CreatePlanCommand(
                0, "补充标准范围", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2),
                "emp-chen", List.of(), List.of(3001L)));
        var startService = new GovernanceTaskStartService(
                taskStore, issueStore, workflowStore, new InMemoryGovernanceRuleCatalog(RULES));
        startService.start(task.id(), task.version(), "emp-admin");
        var executionStore = new InMemoryGovernanceExecutionStore(workflowStore);
        var assets = new InMemoryAssetRepository();
        var executionService = new GovernanceExecutionService(
                executionStore, workflowStore, enabledCatalog, assets,
                new FieldSupplementActionHandler(
                        new ObjectMapper(), new InMemoryDictionaryStore(),
                        new InMemoryGovernanceEmployeeDirectory()),
                Clock.fixed(Instant.parse("2026-07-26T06:00:00Z"), ZoneOffset.UTC));
        var item = executionService.items(task.id()).getFirst().item();
        return new ScopeScenario(executionService, item, assets);
    }

    private record ScopeScenario(
            GovernanceExecutionService service,
            GovernanceItem item,
            InMemoryAssetRepository assetRepository) {}

    private DictionaryItem copyWithStatus(DictionaryItem source, DictionaryStatus status) {
        return new DictionaryItem(
                source.id(), source.category(), source.code(), source.name(), source.parentId(), status,
                source.sortOrder(), source.usageCount(), source.version(), source.description(),
                source.forwardName(), source.reverseName(), source.directional(), source.allowDuplicate(),
                source.mergeTargetId(), LocalDateTime.now());
    }
}
