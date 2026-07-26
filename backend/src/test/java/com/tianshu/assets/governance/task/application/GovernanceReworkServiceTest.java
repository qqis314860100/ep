package com.tianshu.assets.governance.task.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.tianshu.assets.governance.execution.domain.GovernanceItemStatus;
import com.tianshu.assets.governance.execution.domain.GovernanceResultStatus;
import com.tianshu.assets.governance.confirmation.application.GovernanceConfirmationService;
import com.tianshu.assets.governance.support.GovernanceTestFixture;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GovernanceReworkServiceTest {

    private GovernanceTestFixture fixture;
    private GovernanceReworkService service;

    @BeforeEach
    void setUp() {
        fixture = GovernanceTestFixture.fieldClosure();
        service = fixture.reworkService();
    }

    @Test
    void reworkSupersedesRejectedResultAndKeepsHistory() {
        var task = fixture.reworkRequiredTask();
        var reworkItem = fixture.executionStore().items(task.id()).stream()
                .filter(item -> item.status() == GovernanceItemStatus.REWORK_REQUIRED)
                .findFirst().orElseThrow();

        var reopened = service.open(task.id(), task.version(), "责任人信息已修正", "emp-chen");

        assertThat(reopened.status()).isEqualTo(GovernanceTaskStatus.IN_PROGRESS);
        assertThat(reopened.currentRound()).isEqualTo(task.currentRound() + 1);
        assertThat(fixture.executionStore().resultsForItem(reworkItem.id()))
                .extracting(result -> result.status())
                .containsExactly(GovernanceResultStatus.SUPERSEDED, GovernanceResultStatus.DRAFT);
        assertThat(fixture.executionStore().currentResult(reworkItem.id()).reworkReason())
                .isEqualTo("责任人信息已修正");
        assertThat(fixture.executionStore().item(reworkItem.id()).governanceRound())
                .isEqualTo(reopened.currentRound());
        assertThat(fixture.executionStore().items(task.id()).stream()
                .filter(item -> item.id() != reworkItem.id()))
                .allSatisfy(item -> assertThat(item.status()).isEqualTo(GovernanceItemStatus.CONFIRMED));
    }

    @Test
    void resubmissionIncludesOnlyItemsFromTheNewRound() {
        var task = fixture.reworkRequiredTask();
        var reworkItem = fixture.executionStore().items(task.id()).stream()
                .filter(item -> item.status() == GovernanceItemStatus.REWORK_REQUIRED)
                .findFirst().orElseThrow();
        task = service.open(task.id(), task.version(), "修正字段", "emp-chen");
        var draft = fixture.executionStore().currentResult(reworkItem.id());
        fixture.executionService().submit(reworkItem.id(), draft.id(), draft.version(), "emp-chen");

        fixture.taskService().submitForConfirmation(task.id(), task.version());
        var confirmation = fixture.confirmationService().current(task.id());

        assertThat(confirmation.items()).singleElement()
                .extracting(GovernanceConfirmationService.ConfirmationItem::itemId)
                .isEqualTo(reworkItem.id());
    }
}
