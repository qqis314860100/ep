package com.tianshu.assets.governance.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.tianshu.assets.governance.application.GovernanceValidationException;
import com.tianshu.assets.governance.application.GovernanceTaskStateException;
import com.tianshu.assets.governance.execution.domain.GovernanceItemStatus;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceEmployeeDirectory;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceIssueStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceRuleCatalog;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceTaskStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceWorkflowStore;
import com.tianshu.assets.governance.issue.application.GovernanceIssueService;
import com.tianshu.assets.governance.issue.application.GovernanceIssueStore;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import com.tianshu.assets.governance.issue.domain.GovernanceIssue;
import com.tianshu.assets.governance.issue.domain.GovernanceIssueStatus;
import com.tianshu.assets.governance.support.GovernanceTestFixture;
import com.tianshu.assets.governance.task.domain.GovernancePlan;
import com.tianshu.assets.governance.task.domain.GovernanceScopeItem;
import com.tianshu.assets.governance.task.domain.GovernanceRuleSnapshot;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GovernanceTaskStartServiceTest {

    private GovernanceTestFixture fixture;
    private GovernanceTaskStartService service;

    @BeforeEach
    void setUp() {
        fixture = GovernanceTestFixture.fieldClosure();
        service = fixture.startService();
    }

    @Test
    void startReportsDuplicateAssignmentsAndCyclicDependenciesTogether() {
        var task = fixture.closedLoopDraftWithTwoIssues();
        fixture.addPlan(task.id(), 11L, List.of(1001L), List.of(12L));
        fixture.addPlan(task.id(), 12L, List.of(1001L, 1002L), List.of(11L));

        assertThatThrownBy(() -> service.start(task.id(), task.version(), "emp-admin"))
                .isInstanceOf(GovernanceValidationException.class)
                .hasMessageContaining("治理项不能重复分配")
                .hasMessageContaining("计划依赖不能形成环");
    }

    @Test
    void startFreezesIssueAssetAndRuleVersions() {
        var task = fixture.validDraft();

        var started = service.start(task.id(), task.version(), "emp-admin");

        assertThat(started.status()).isEqualTo(GovernanceTaskStatus.IN_PROGRESS);
        assertThat(fixture.workflowStore().scopeItems(started.scopeSnapshotId()))
                .extracting(GovernanceScopeItem::assetVersion, GovernanceScopeItem::ruleVersion)
                .containsExactly(tuple(7L, 3L), tuple(9L, 3L));
    }

    @Test
    void startRejectsEmptyPlansWithoutWritingWorkflowState() {
        var task = fixture.closedLoopDraftWithTwoIssues();

        assertThatThrownBy(() -> service.start(task.id(), task.version(), "emp-admin"))
                .isInstanceOf(GovernanceValidationException.class)
                .hasMessage("至少需要一个治理计划");

        assertThat(fixture.taskStore().findById(task.id()).orElseThrow().status())
                .isEqualTo(GovernanceTaskStatus.DRAFT);
        assertThat(fixture.workflowStore().items(task.id())).isEmpty();
    }

    @Test
    void startRejectsMissingAssignmentAndMissingDependencyTogether() {
        var task = fixture.closedLoopDraftWithTwoIssues();
        fixture.addPlan(task.id(), 11L, List.of(1001L), List.of(99L));

        assertThatThrownBy(() -> service.start(task.id(), task.version(), "emp-admin"))
                .isInstanceOf(GovernanceValidationException.class)
                .hasMessageContaining("治理项必须全部分配")
                .hasMessageContaining("前置计划必须属于同一治理任务");
        assertThat(fixture.workflowStore().items(task.id())).isEmpty();
    }

    @Test
    void startRejectsSelfDependencyFromPersistedPlan() {
        var task = fixture.closedLoopDraftWithTwoIssues();
        fixture.addPlan(task.id(), 11L, List.of(1001L, 1002L), List.of(11L));

        assertThatThrownBy(() -> service.start(task.id(), task.version(), "emp-admin"))
                .isInstanceOf(GovernanceValidationException.class)
                .hasMessageContaining("计划不能依赖自身")
                .hasMessageContaining("计划依赖不能形成环");
        assertThat(fixture.workflowStore().items(task.id())).isEmpty();
    }

    @Test
    void startRejectsDependencyOnPlanFromAnotherTaskWithoutFreezingAnything() {
        var issueStore = new InMemoryGovernanceIssueStore();
        issueStore.insertAll(List.of(
                new GovernanceIssue(
                        1001, 101, GovernanceField.DESCRIPTION, "MISSING_DESCRIPTION", "/description",
                        "FIELD_REQUIRED", 3, "\"\"", 7, "asset:101:v7", "HIGH", true,
                        GovernanceIssueStatus.OPEN, null, 0),
                new GovernanceIssue(
                        1002, 102, GovernanceField.SPECIALTIES, "MISSING_SPECIALTIES", "/specialties",
                        "FIELD_REQUIRED", 3, "[]", 9, "asset:102:v9", "HIGH", true,
                        GovernanceIssueStatus.OPEN, null, 0)));
        var taskStore = new InMemoryGovernanceTaskStore();
        var issueService = new GovernanceIssueService(issueStore, taskStore);
        var taskService = new GovernanceTaskApplicationService(
                taskStore, new InMemoryGovernanceEmployeeDirectory(), issueStore);
        var workflowStore = new InMemoryGovernanceWorkflowStore();
        var startService = new GovernanceTaskStartService(
                taskStore, issueStore, workflowStore,
                new InMemoryGovernanceRuleCatalog(new GovernanceRuleSnapshot(
                        0, "FIELD-COMPLETENESS", 3, 3, Map.of(), "FIELD-QUALITY", 2)));
        var taskA = issueService.createTask(new GovernanceIssueService.CreateGovernanceTaskCommand(
                "任务 A", List.of(1001L), "emp-chen", "陈工", LocalDate.of(2026, 9, 30)));
        var taskB = issueService.createTask(new GovernanceIssueService.CreateGovernanceTaskCommand(
                "任务 B", List.of(1002L), "emp-li", "李工", LocalDate.of(2026, 9, 30)));
        var taskBPlan = taskService.createPlan(taskB.id(), new GovernanceTaskApplicationService.CreatePlanCommand(
                0, "任务 B 计划", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2),
                "emp-li", List.of(), List.of(1002L)));
        taskStore.insertPlan(GovernancePlan.closedLoop(
                11, taskA.id(), 0, "任务 A 计划", "emp-chen",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2),
                List.of(taskBPlan.id()), List.of(1001L), 0));

        assertThatThrownBy(() -> startService.start(taskA.id(), taskA.version(), "emp-admin"))
                .isInstanceOf(GovernanceValidationException.class)
                .hasMessageContaining("前置计划必须属于同一治理任务");
        assertThat(taskStore.findById(taskA.id()).orElseThrow().status()).isEqualTo(GovernanceTaskStatus.DRAFT);
        assertThat(workflowStore.scopeItems(1)).isEmpty();
        assertThat(workflowStore.items(taskA.id())).isEmpty();
        assertThatThrownBy(() -> workflowStore.scopeSnapshot(1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("治理范围快照不存在");
    }

    @Test
    void planCreationRejectsIncompleteFieldsDuplicatesAndMissingDependencies() {
        var task = fixture.closedLoopDraftWithTwoIssues();

        assertThatThrownBy(() -> fixture.taskService().createPlan(
                task.id(), new GovernanceTaskApplicationService.CreatePlanCommand(
                        0, " ", LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1),
                        " ", List.of(), List.of(1001L, 1001L))))
                .isInstanceOf(GovernanceValidationException.class)
                .hasMessageContaining("计划名称不能为空")
                .hasMessageContaining("计划责任人不能为空")
                .hasMessageContaining("计划结束日期不能早于开始日期")
                .hasMessageContaining("不能重复");

        assertThatThrownBy(() -> fixture.taskService().createPlan(
                task.id(), new GovernanceTaskApplicationService.CreatePlanCommand(
                        0, "补充字段", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2),
                        "emp-chen", List.of(99L), List.of(1001L, 1002L))))
                .isInstanceOf(GovernanceValidationException.class)
                .hasMessage("前置计划必须属于同一治理任务");
    }

    @Test
    void startRejectsStaleTaskVersionBeforeFreezing() {
        var task = fixture.validDraft();

        assertThatThrownBy(() -> service.start(task.id(), task.version() + 1, "emp-admin"))
                .isInstanceOf(GovernanceTaskStateException.class)
                .hasMessageContaining("刷新后重试");
        assertThat(fixture.workflowStore().items(task.id())).isEmpty();
    }

    @Test
    void startCreatesPendingItemsAndImmutableSnapshotWithOneVersionIncrement() {
        var task = fixture.validDraft();

        var started = service.start(task.id(), task.version(), "emp-admin");

        assertThat(started.version()).isEqualTo(task.version() + 1);
        assertThat(started.currentRound()).isEqualTo(task.currentRound());
        assertThat(started.scopeSnapshotId()).isNotNull();
        assertThat(started.qualityPolicySnapshotId()).isNotNull();
        assertThat(fixture.workflowStore().items(task.id()))
                .extracting(item -> item.status())
                .containsExactly(GovernanceItemStatus.PENDING, GovernanceItemStatus.PENDING);
        var snapshot = fixture.workflowStore().scopeSnapshot(started.scopeSnapshotId());
        assertThat(snapshot.createdBy()).isEqualTo("emp-admin");
        assertThat(snapshot.claimedIssueIds()).containsExactly(1001L, 1002L);
        assertThatThrownBy(() -> snapshot.claimedIssueIds().add(999L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.ruleSnapshot().dictionaryVersions().put("owner", 10L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void secondStartConflictsAndPlanCreationIsLockedAfterStart() {
        var task = fixture.validDraft();
        var started = service.start(task.id(), task.version(), "emp-admin");

        assertThatThrownBy(() -> service.start(started.id(), started.version(), "emp-admin"))
                .isInstanceOf(GovernanceTaskStateException.class)
                .hasMessage("只有草稿治理任务可以启动");
        assertThatThrownBy(() -> fixture.addPlan(
                started.id(), 12L, List.of(1001L), List.of()))
                .isInstanceOf(GovernanceTaskStateException.class)
                .hasMessage("治理任务启动后计划已锁定");
        assertThat(fixture.workflowStore().items(task.id())).hasSize(2);
    }

    @Test
    void issueVersionChangeImmediatelyBeforeFreezeLeavesTaskDraftAndWorkflowEmpty() {
        var delegate = new InMemoryGovernanceIssueStore();
        delegate.insertAll(List.of(new GovernanceIssue(
                1001, 101, GovernanceField.DESCRIPTION, "MISSING_DESCRIPTION", "/description",
                "FIELD_REQUIRED", 3, "\"\"", 7, "asset:101:v7", "HIGH", true,
                GovernanceIssueStatus.OPEN, null, 0)));
        var issueStore = new ChangingIssueStore(delegate);
        var taskStore = new InMemoryGovernanceTaskStore();
        var issueService = new GovernanceIssueService(issueStore, taskStore);
        var taskService = new GovernanceTaskApplicationService(
                taskStore, new InMemoryGovernanceEmployeeDirectory(), issueStore);
        var workflowStore = new InMemoryGovernanceWorkflowStore();
        var startService = new GovernanceTaskStartService(
                taskStore, issueStore, workflowStore,
                new InMemoryGovernanceRuleCatalog(new GovernanceRuleSnapshot(
                        0, "FIELD-COMPLETENESS", 3, 3, Map.of(), "FIELD-QUALITY", 2)));
        var task = issueService.createTask(new GovernanceIssueService.CreateGovernanceTaskCommand(
                "资产并发变化", List.of(1001L), "emp-chen", "陈工", LocalDate.of(2026, 9, 30)));
        taskService.createPlan(task.id(), new GovernanceTaskApplicationService.CreatePlanCommand(
                0, "补充说明", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2),
                "emp-chen", List.of(), List.of(1001L)));
        issueStore.changeOnReload = true;

        assertThatThrownBy(() -> startService.start(task.id(), task.version(), "emp-admin"))
                .isInstanceOf(GovernanceValidationException.class)
                .hasMessage("治理问题已变化，请刷新计划后重试");
        assertThat(taskStore.findById(task.id()).orElseThrow().status()).isEqualTo(GovernanceTaskStatus.DRAFT);
        assertThat(workflowStore.items(task.id())).isEmpty();
    }

    @Test
    void startHandlesDeepAcyclicPlanChainWithoutStackOverflow() {
        var issueStore = new InMemoryGovernanceIssueStore();
        var issues = java.util.stream.LongStream.rangeClosed(1, 2_000)
                .mapToObj(index -> new GovernanceIssue(
                        20_000 + index, 30_000 + index, GovernanceField.DESCRIPTION,
                        "MISSING_DESCRIPTION", "/description", "FIELD_REQUIRED", 3,
                        "\"\"", 1, "asset:" + (30_000 + index) + ":v1", "HIGH", true,
                        GovernanceIssueStatus.OPEN, null, 0))
                .toList();
        issueStore.insertAll(issues);
        var taskStore = new InMemoryGovernanceTaskStore();
        var issueService = new GovernanceIssueService(issueStore, taskStore);
        var workflowStore = new InMemoryGovernanceWorkflowStore();
        var startService = new GovernanceTaskStartService(
                taskStore, issueStore, workflowStore,
                new InMemoryGovernanceRuleCatalog(new GovernanceRuleSnapshot(
                        0, "FIELD-COMPLETENESS", 3, 3, Map.of(), "FIELD-QUALITY", 2)));
        var issueIds = issues.stream().map(GovernanceIssue::id).toList();
        var task = issueService.createTask(new GovernanceIssueService.CreateGovernanceTaskCommand(
                "深依赖链", issueIds, "emp-chen", "陈工", LocalDate.of(2026, 9, 30)));
        for (int sequence = 1; sequence <= issues.size(); sequence++) {
            var planId = 40_000L + sequence;
            var dependencyIds = sequence == 1 ? List.<Long>of() : List.of(planId - 1);
            taskStore.insertPlan(GovernancePlan.closedLoop(
                    planId, task.id(), sequence, "计划 " + sequence, "emp-chen",
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), dependencyIds,
                    List.of(issues.get(sequence - 1).id()), 0));
        }

        var started = startService.start(task.id(), task.version(), "emp-admin");

        assertThat(started.status()).isEqualTo(GovernanceTaskStatus.IN_PROGRESS);
        assertThat(workflowStore.items(task.id())).hasSize(2_000);
    }

    private static final class ChangingIssueStore implements GovernanceIssueStore {
        private final InMemoryGovernanceIssueStore delegate;
        private boolean changeOnReload;

        private ChangingIssueStore(InMemoryGovernanceIssueStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<GovernanceIssue> find(
                GovernanceField field, GovernanceIssueStatus status, Long assetId) {
            return delegate.find(field, status, assetId);
        }

        @Override
        public List<GovernanceIssue> findByIds(List<Long> issueIds) {
            var issues = delegate.findByIds(issueIds);
            if (!changeOnReload) return issues;
            return issues.stream().map(issue -> new GovernanceIssue(
                    issue.id(), issue.assetId(), issue.targetField(), issue.issueType(), issue.targetPath(),
                    issue.ruleCode(), issue.ruleVersion(), issue.originalFactJson(), issue.assetVersion() + 1,
                    issue.scopeFingerprint(), issue.severity(), issue.blocking(), issue.status(),
                    issue.taskId(), issue.version())).toList();
        }

        @Override
        public List<GovernanceIssue> insertAll(List<GovernanceIssue> issues) {
            return delegate.insertAll(issues);
        }

        @Override
        public void claimOpen(List<GovernanceIssue> expectedIssues, long taskId) {
            delegate.claimOpen(expectedIssues, taskId);
        }

        @Override
        public List<GovernanceIssue> findClaimedByTask(long taskId) {
            return delegate.findClaimedByTask(taskId);
        }
    }
}
