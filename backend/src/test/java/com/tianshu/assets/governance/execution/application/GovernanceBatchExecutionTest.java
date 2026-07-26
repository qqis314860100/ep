package com.tianshu.assets.governance.execution.application;

import static com.tianshu.assets.governance.execution.application.BatchItemResult.BatchOutcome.CONFLICT;
import static com.tianshu.assets.governance.execution.application.BatchItemResult.BatchOutcome.SUCCESS;
import static com.tianshu.assets.governance.execution.application.BatchItemResult.BatchOutcome.VALIDATION_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.execution.domain.GovernanceItemStatus;
import com.tianshu.assets.governance.support.GovernanceTestFixture;
import com.tianshu.assets.governance.task.application.GovernanceTaskApplicationService;
import com.tianshu.assets.governance.task.domain.GovernancePlanStatus;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GovernanceBatchExecutionTest {

    private GovernanceTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = GovernanceTestFixture.batchFieldClosure();
    }

    @Test
    void batchReturnsSuccessValidationFailureAndConflictIndependently() {
        var items = fixture.executionService().items(fixture.validStartedTask().id()).stream()
                .map(GovernanceExecutionService.ItemExecutionContext::item)
                .toList();

        var response = fixture.executionService().batchResults("batch-20260726-001", List.of(
                fixture.validDescriptionCommand(items.get(0).id()),
                fixture.blankDescriptionCommand(items.get(1).id()),
                fixture.staleVersionCommand(items.get(2).id())));

        assertThat(response.results()).extracting(BatchItemResult::outcome)
                .containsExactly(SUCCESS, VALIDATION_FAILED, CONFLICT);
        assertThat(fixture.executionStore().item(items.get(0).id()).status())
                .isEqualTo(GovernanceItemStatus.SUBMITTED);
        assertThat(fixture.executionStore().item(items.get(1).id()).status())
                .isEqualTo(GovernanceItemStatus.PENDING);
    }

    @Test
    void sameIdempotencyKeyAndDigestReturnsSavedResultsWithoutNewVersion() {
        var item = fixture.batchItems().getFirst();
        var command = fixture.validDescriptionCommand(item.id());

        var first = fixture.executionService().batchResults("same-key", List.of(command));
        var saved = fixture.executionStore().currentResult(item.id());
        var repeated = fixture.executionService().batchResults("same-key", List.of(command));

        assertThat(repeated).isEqualTo(first);
        assertThat(fixture.executionStore().currentResult(item.id())).isEqualTo(saved);
    }

    @Test
    void sameIdempotencyKeyWithDifferentDigestConflicts() {
        var item = fixture.batchItems().getFirst();
        fixture.executionService().batchResults(
                "reused-key", List.of(fixture.validDescriptionCommand(item.id())));

        assertThatThrownBy(() -> fixture.executionService().batchResults(
                "reused-key", List.of(fixture.descriptionCommand(item.id(), "另一个说明"))))
                .isInstanceOf(GovernanceConflictException.class)
                .hasMessage("幂等键已用于不同批量请求");
    }

    @Test
    void batchRequiresOneFieldStandardVersionAndScopeFingerprintPerItem() {
        var items = fixture.batchItems();
        var valid = fixture.validDescriptionCommand(items.get(0).id());

        var response = fixture.executionService().batchResults("mixed-batch", List.of(
                valid,
                fixture.withTargetField(valid, items.get(1).id()),
                fixture.withStandardVersion(valid, items.get(2).id()),
                fixture.withScopeFingerprint(valid, items.get(3).id())));

        assertThat(response.results()).extracting(BatchItemResult::outcome)
                .containsExactly(SUCCESS, VALIDATION_FAILED, VALIDATION_FAILED, VALIDATION_FAILED);
        assertThat(response.results().subList(1, 4)).allMatch(result -> result.errorCode().equals("batch_constraint"));
    }

    @Test
    void nullBatchElementsFailIndependentlyAndValidElementsStillRun() {
        var item = fixture.batchItems().getFirst();

        var response = fixture.executionService().batchResults(
                "batch-with-null", java.util.Arrays.asList(
                        null, fixture.validDescriptionCommand(item.id()), null));

        assertThat(response.results()).extracting(BatchItemResult::outcome)
                .containsExactly(VALIDATION_FAILED, SUCCESS, VALIDATION_FAILED);
    }

    @Test
    void distinguishesVersionConflictsFromGeneralStateConflicts() {
        var items = fixture.batchItems();
        var stale = fixture.executionService().batchResults(
                "stale-version", List.of(fixture.staleVersionCommand(items.get(0).id())));
        fixture.markSubmitted(items.get(1).id());
        var alreadySubmitted = fixture.executionService().batchResults(
                "already-submitted", List.of(fixture.validDescriptionCommand(items.get(1).id())));

        assertThat(stale.results().getFirst().errorCode()).isEqualTo("version_conflict");
        assertThat(alreadySubmitted.results().getFirst().errorCode()).isEqualTo("conflict");
    }

    @Test
    void unknownItemFailsIndependentlyAndReplayReturnsSavedBatchWithoutNewVersions() {
        var items = fixture.batchItems();
        var first = fixture.validDescriptionCommand(items.get(0).id());
        var third = fixture.validDescriptionCommand(items.get(2).id());
        var unknown = new GovernanceExecutionService.BatchResultCommand(
                999_999, 0, first.assetVersion(), first.targetField(), first.standardVersion(),
                first.scopeFingerprint(), first.proposedValueJson(), first.actorUserId());

        var response = fixture.executionService().batchResults(
                "batch-with-unknown", List.of(first, unknown, third));
        var firstSaved = fixture.executionStore().currentResult(first.itemId());
        var thirdSaved = fixture.executionStore().currentResult(third.itemId());
        var replay = fixture.executionService().batchResults(
                "batch-with-unknown", List.of(first, unknown, third));

        assertThat(response.results()).extracting(BatchItemResult::outcome)
                .containsExactly(SUCCESS, VALIDATION_FAILED, SUCCESS);
        assertThat(response.results().get(1).errorCode()).isEqualTo("item_not_found");
        assertThat(replay).isEqualTo(response);
        assertThat(fixture.executionStore().currentResult(first.itemId())).isEqualTo(firstSaved);
        assertThat(fixture.executionStore().currentResult(third.itemId())).isEqualTo(thirdSaved);
    }

    @Test
    void taskCanEnterConfirmationOnlyWhenEveryItemIsSubmittedAndNoneIsBlocked() {
        var task = fixture.validStartedTask();
        fixture.markSubmitted(fixture.batchItems().get(0).id());
        fixture.markBlocked(fixture.batchItems().get(1).id(), "负责人无法确认");

        assertThatThrownBy(() -> fixture.taskService().submitForConfirmation(task.id(), task.version()))
                .hasMessage("仍有阻塞或未提交治理项");
        assertThat(fixture.taskStore().findById(task.id()).orElseThrow().status())
                .isEqualTo(GovernanceTaskStatus.IN_PROGRESS);
    }

    @Test
    void allSubmittedItemsMoveTaskToPendingConfirmation() {
        var task = fixture.validStartedTask();
        fixture.batchItems().forEach(item -> fixture.markSubmitted(item.id()));

        var submitted = fixture.taskService().submitForConfirmation(task.id(), task.version());

        assertThat(submitted.status()).isEqualTo(GovernanceTaskStatus.PENDING_CONFIRMATION);
        assertThat(submitted.version()).isEqualTo(task.version() + 1);
    }

    @Test
    void detailAggregatesThreeStageProgressRisksPlansSnapshotsAndWorkbenchEntries() {
        var task = fixture.validStartedTask();
        var items = fixture.batchItems();
        fixture.markSubmitted(items.get(0).id());
        fixture.markBlocked(items.get(1).id(), "等待责任人");

        var detail = fixture.taskService().detail(task.id());

        assertThat(detail.progress().total()).isEqualTo(4);
        assertThat(detail.progress().submitted()).isEqualTo(1);
        assertThat(detail.progress().confirmed()).isZero();
        assertThat(detail.progress().accepted()).isZero();
        assertThat(detail.riskCount()).isEqualTo(1);
        assertThat(detail.scopeSnapshot().itemCount()).isEqualTo(4);
        assertThat(detail.ruleSnapshot().dataStandardVersion()).isEqualTo(3);
        assertThat(detail.workbenchEntries()).containsEntry(
                        "execution", "/sys/drawing/tasks/" + task.id() + "/execute")
                .containsEntry("confirmation", "/sys/drawing/tasks/" + task.id() + "/confirm")
                .containsEntry("acceptance", "/sys/drawing/tasks/" + task.id() + "/accept");
        assertThat(detail.plans()).extracting(GovernanceTaskApplicationService.PlanProjection::status)
                .containsExactly(GovernancePlanStatus.IN_PROGRESS);
    }

    @Test
    void submittedPlansRemainBlockedUntilDirectAndTransitiveDependenciesAreDoneRegardlessOfOrder() {
        var task = fixture.closedLoopDraftWithTwoIssues();
        fixture.addPlan(task.id(), 503, List.of(1003L, 1004L), List.of(502L));
        fixture.addPlan(task.id(), 501, List.of(1001L), List.of());
        fixture.addPlan(task.id(), 502, List.of(1002L), List.of(501L));
        fixture.startService().start(task.id(), task.version(), "emp-admin");
        fixture.batchItems().stream()
                .filter(item -> item.planId() == 502 || item.planId() == 503)
                .forEach(item -> fixture.markSubmitted(item.id()));

        var detail = fixture.taskService().detail(task.id());

        assertThat(detail.plans()).extracting(
                        projection -> projection.plan().id(),
                        GovernanceTaskApplicationService.PlanProjection::status)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(501L, GovernancePlanStatus.NOT_STARTED),
                        org.assertj.core.groups.Tuple.tuple(502L, GovernancePlanStatus.BLOCKED),
                        org.assertj.core.groups.Tuple.tuple(503L, GovernancePlanStatus.BLOCKED));
    }

    @Test
    void taskListCombinesTaskAndSameItemScopeFilters() {
        var task = fixture.validStartedTask();

        assertThat(fixture.taskService().list(new GovernanceTaskApplicationService.TaskFilter(
                GovernanceTaskStatus.IN_PROGRESS, "emp-chen", LocalDate.of(2026, 10, 1),
                fixture.batchItems().getFirst().targetField(), fixture.batchItems().getFirst().scopeFingerprint())))
                .extracting(projection -> projection.task().id())
                .containsExactly(task.id());
        assertThat(fixture.taskService().list(new GovernanceTaskApplicationService.TaskFilter(
                GovernanceTaskStatus.IN_PROGRESS, "emp-chen", LocalDate.of(2026, 10, 1),
                fixture.batchItems().getFirst().targetField(), "other-scope")))
                .isEmpty();
    }
}
