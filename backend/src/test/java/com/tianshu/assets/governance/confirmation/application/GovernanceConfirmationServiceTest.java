package com.tianshu.assets.governance.confirmation.application;

import static com.tianshu.assets.governance.confirmation.domain.GovernanceConfirmationDecision.Decision.APPROVED;
import static com.tianshu.assets.governance.confirmation.domain.GovernanceConfirmationDecision.Decision.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.application.GovernanceTaskStateException;
import com.tianshu.assets.governance.application.GovernanceValidationException;
import com.tianshu.assets.governance.confirmation.application.GovernanceConfirmationService.DecisionCommand;
import com.tianshu.assets.governance.confirmation.domain.GovernanceConfirmationRound;
import com.tianshu.assets.governance.execution.domain.GovernanceItemStatus;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceConfirmationStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceEmployeeDirectory;
import com.tianshu.assets.governance.support.GovernanceTestFixture;
import com.tianshu.assets.governance.task.application.GovernanceTaskApplicationService;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GovernanceConfirmationServiceTest {

    private GovernanceTestFixture fixture;
    private GovernanceConfirmationService service;

    @BeforeEach
    void setUp() {
        fixture = GovernanceTestFixture.fieldClosure();
        service = fixture.confirmationService();
    }

    @Test
    void submissionCreatesOneQueryableRoundFromCurrentSubmittedResults() {
        var task = submittedTask();
        var submitted = fixture.taskService().submitForConfirmation(task.id(), task.version());
        var round = service.current(task.id()).round();
        var current = service.current(round.taskId());

        assertThat(current.round().id()).isEqualTo(round.id());
        assertThat(current.items()).hasSize(2);
        assertThat(current.items()).allSatisfy(item -> {
            assertThat(item.resultVersionId()).isPositive();
            assertThat(item.resultType()).isNotBlank();
        });
        assertThat(current.coveredCount()).isZero();
        assertThat(current.coverageRate()).isZero();
        assertThat(current.approvalRate()).isZero();
        var replayed = fixture.taskService().submitForConfirmation(task.id(), task.version());
        assertThat(replayed).isEqualTo(submitted);
        assertThat(service.current(round.taskId()).round().id()).isEqualTo(round.id());
    }

    @Test
    void roundCreationFailureLeavesTaskInProgress() {
        var task = submittedTask();
        var failingConfirmationStore = new InMemoryGovernanceConfirmationStore() {
            @Override
            public synchronized GovernanceConfirmationRound createRound(
                    long taskId, int governanceRound, Map<Long, Long> resultVersionIds, Instant createdAt) {
                throw new GovernanceConflictException("确认轮次创建失败");
            }
        };
        var taskService = taskService(fixture.taskStore(), failingConfirmationStore);

        assertThatThrownBy(() -> taskService.submitForConfirmation(task.id(), task.version()))
                .isInstanceOf(GovernanceConflictException.class)
                .hasMessage("确认轮次创建失败");

        assertThat(fixture.taskStore().findById(task.id()).orElseThrow().status())
                .isEqualTo(GovernanceTaskStatus.IN_PROGRESS);
        assertThat(failingConfirmationStore.currentRound(task.id())).isEmpty();
    }

    @Test
    void taskUpdateFailureDiscardsNewConfirmationRound() {
        var task = submittedTask();
        var confirmationStore = new InMemoryGovernanceConfirmationStore();
        var failingTaskStore = spy(fixture.taskStore());
        doThrow(new GovernanceTaskStateException("任务更新失败"))
                .when(failingTaskStore).update(any(GovernanceTask.class), anyLong());
        var taskService = taskService(failingTaskStore, confirmationStore);

        assertThatThrownBy(() -> taskService.submitForConfirmation(task.id(), task.version()))
                .isInstanceOf(GovernanceTaskStateException.class)
                .hasMessage("任务更新失败");

        assertThat(confirmationStore.currentRound(task.id())).isEmpty();
        assertThat(fixture.taskStore().findById(task.id()).orElseThrow().status())
                .isEqualTo(GovernanceTaskStatus.IN_PROGRESS);
    }

    @Test
    void missingConfirmationStoreCannotMoveTaskToPendingConfirmation() {
        var task = submittedTask();
        var taskService = new GovernanceTaskApplicationService(
                fixture.taskStore(), new InMemoryGovernanceEmployeeDirectory(), fixture.issueStore(),
                fixture.workflowStore(), fixture.executionStore());

        assertThatThrownBy(() -> taskService.submitForConfirmation(task.id(), task.version()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("治理确认存储未配置");
        assertThat(fixture.taskStore().findById(task.id()).orElseThrow().status())
                .isEqualTo(GovernanceTaskStatus.IN_PROGRESS);
    }

    @Test
    void confirmationRequiresCoverageAndCreatesReworkForRejectedItems() {
        var round = fixture.pendingConfirmationWithTwoItems();
        var items = service.current(round.taskId()).items();
        service.decide(round.id(), items.get(0).itemId(),
                new DecisionCommand(APPROVED, "", 0, items.get(0).responsibleUserId()));

        assertThatThrownBy(() -> service.complete(round.taskId(), round.id(), round.version()))
                .hasMessage("确认决定尚未覆盖全部治理项");

        service.decide(round.id(), items.get(1).itemId(),
                new DecisionCommand(REJECTED, "专业类别不符合资产用途", 0,
                        items.get(1).responsibleUserId()));
        var result = service.complete(round.taskId(), round.id(), round.version());

        assertThat(result.taskStatus()).isEqualTo(GovernanceTaskStatus.REWORK_REQUIRED);
        assertThat(fixture.executionStore().item(items.get(0).itemId()).status())
                .isEqualTo(GovernanceItemStatus.CONFIRMED);
        assertThat(fixture.executionStore().item(items.get(1).itemId()).status())
                .isEqualTo(GovernanceItemStatus.REWORK_REQUIRED);
        assertThat(result.coveredCount()).isEqualTo(2);
        assertThat(result.approvedCount()).isEqualTo(1);
        assertThat(result.coverageRate()).isEqualTo(1.0);
        assertThat(result.approvalRate()).isEqualTo(0.5);
    }

    @Test
    void allApprovedMovesItemsAndTaskToAcceptance() {
        var round = fixture.pendingConfirmationWithTwoItems();
        var items = service.current(round.taskId()).items();
        items.forEach(item -> service.decide(round.id(), item.itemId(),
                new DecisionCommand(APPROVED, null, 0, item.responsibleUserId())));

        var result = service.complete(round.taskId(), round.id(), round.version());

        assertThat(result.taskStatus()).isEqualTo(GovernanceTaskStatus.PENDING_ACCEPTANCE);
        assertThat(items).allSatisfy(item -> assertThat(
                fixture.executionStore().item(item.itemId()).status()).isEqualTo(GovernanceItemStatus.CONFIRMED));
        assertThatThrownBy(() -> service.complete(round.taskId(), round.id(), round.version()))
                .isInstanceOf(GovernanceConflictException.class);
    }

    @Test
    void decisionsAreImmutableAndRejectionsRequireComments() {
        var round = fixture.pendingConfirmationWithTwoItems();
        var item = service.current(round.taskId()).items().get(0);

        assertThatThrownBy(() -> service.decide(round.id(), item.itemId(),
                new DecisionCommand(REJECTED, " ", 0, item.responsibleUserId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("退回必须填写确认意见");

        var decided = service.decide(round.id(), item.itemId(),
                new DecisionCommand(APPROVED, "", 0, item.responsibleUserId()));
        assertThat(decided.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.resultVersionId()).isEqualTo(item.resultVersionId());
            assertThat(decision.version()).isZero();
        });
        assertThatThrownBy(() -> service.decide(round.id(), item.itemId(),
                new DecisionCommand(REJECTED, "改为退回", 0, item.responsibleUserId())))
                .isInstanceOf(GovernanceConflictException.class)
                .hasMessage("本轮确认决定已保存，不能覆盖");
    }

    @Test
    void confirmerMustBeCurrentAssetOwner() {
        var round = fixture.pendingConfirmationWithTwoItems();
        var item = service.current(round.taskId()).items().get(0);

        assertThatThrownBy(() -> service.decide(round.id(), item.itemId(),
                new DecisionCommand(APPROVED, "", 0, "other-owner")))
                .isInstanceOf(GovernanceValidationException.class)
                .hasMessage("确认人不是资产当前有效责任人");
    }

    @Test
    void batchApprovalRequiresSameResultTypeAndResponsibilityScope() {
        var mixed = fixture.pendingConfirmationWithTwoItems();
        var mixedItems = service.current(mixed.taskId()).items();
        assertThatThrownBy(() -> service.batchApprove(
                mixed.id(), mixedItems.stream().map(item -> item.itemId()).toList(), "owner-1"))
                .isInstanceOf(GovernanceValidationException.class)
                .hasMessage("批量通过仅支持同一结果类型和责任范围");

        fixture = GovernanceTestFixture.batchFieldClosure();
        service = fixture.confirmationService();
        var same = fixture.pendingConfirmationWithTwoItems();
        var sameItems = service.current(same.taskId()).items();
        var decided = service.batchApprove(
                same.id(), List.of(sameItems.get(0).itemId(), sameItems.get(1).itemId()), "owner-1");
        assertThat(decided.coveredCount()).isEqualTo(2);
        assertThat(decided.decisions()).allSatisfy(decision -> assertThat(decision.version()).isZero());
    }

    @Test
    void batchApprovalDoesNotPartiallySaveWhenAnyItemWasAlreadyDecided() {
        fixture = GovernanceTestFixture.batchFieldClosure();
        service = fixture.confirmationService();
        var round = fixture.pendingConfirmationWithTwoItems();
        var items = service.current(round.taskId()).items();
        service.decide(round.id(), items.get(0).itemId(),
                new DecisionCommand(APPROVED, "", 0, "owner-1"));

        assertThatThrownBy(() -> service.batchApprove(
                round.id(), List.of(items.get(1).itemId(), items.get(0).itemId()), "owner-1"))
                .isInstanceOf(GovernanceConflictException.class);

        assertThat(service.current(round.taskId()).decisions()).hasSize(1);
    }

    private GovernanceTask submittedTask() {
        var task = fixture.validStartedTask();
        fixture.executionService().items(task.id()).stream()
                .forEach(context -> fixture.markSubmitted(context.item().id()));
        return fixture.taskStore().findById(task.id()).orElseThrow();
    }

    private GovernanceTaskApplicationService taskService(
            com.tianshu.assets.governance.task.application.GovernanceTaskStore taskStore,
            GovernanceConfirmationStore confirmationStore) {
        return new GovernanceTaskApplicationService(
                taskStore, new InMemoryGovernanceEmployeeDirectory(), fixture.issueStore(),
                fixture.workflowStore(), fixture.executionStore(), confirmationStore);
    }
}
