package com.tianshu.assets.governance.acceptance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceRound;
import com.tianshu.assets.governance.acceptance.domain.GovernanceQualityMetric;
import com.tianshu.assets.governance.application.GovernanceValidationException;
import com.tianshu.assets.governance.execution.domain.GovernanceItemStatus;
import com.tianshu.assets.governance.support.GovernanceTestFixture;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GovernanceAcceptanceServiceTest {

    private GovernanceTestFixture fixture;
    private GovernanceAcceptanceService service;

    @BeforeEach
    void setUp() {
        fixture = GovernanceTestFixture.fieldClosure();
        service = fixture.acceptanceService();
    }

    @Test
    void failedMetricReturnsOnlyAffectedItemsToRework() {
        var round = fixture.pendingAcceptance();
        var items = fixture.executionStore().items(round.taskId());
        var affected = items.get(1).id();
        fixture.passAllSamples(round.id());
        round = fixture.failMetric(round.id(), GovernanceQualityMetric.OWNER_COVERAGE, List.of(affected));

        var result = service.complete(round.taskId(), round.id(), round.version(), "qa-1");

        assertThat(result.taskStatus()).isEqualTo(GovernanceTaskStatus.REWORK_REQUIRED);
        assertThat(fixture.executionStore().item(items.get(0).id()).status())
                .isEqualTo(GovernanceItemStatus.CONFIRMED);
        assertThat(fixture.executionStore().item(affected).status())
                .isEqualTo(GovernanceItemStatus.REWORK_REQUIRED);
        assertThat(result.affectedItemIds()).containsExactly(affected);
        assertThat(result.applicationJobId()).isNull();
    }

    @Test
    void passingAcceptanceKeepsTaskPendingAndCreatesApplicationJob() {
        var round = fixture.pendingAcceptance();
        fixture.passAllSamples(round.id());
        round = service.current(round.taskId());

        var result = service.complete(round.taskId(), round.id(), round.version(), "qa-1");

        assertThat(result.roundStatus()).isEqualTo(GovernanceAcceptanceRound.Status.PASSED);
        assertThat(result.taskStatus()).isEqualTo(GovernanceTaskStatus.PENDING_ACCEPTANCE);
        assertThat(result.applicationJobId()).isPositive();
        assertThat(fixture.executionStore().items(round.taskId()))
                .allSatisfy(item -> assertThat(item.status()).isEqualTo(GovernanceItemStatus.ACCEPTED));
        assertThat(fixture.acceptanceStore().applicationJob(result.applicationJobId())).isPresent();
    }

    @Test
    void completionRequiresEveryFixedSampleToBeChecked() {
        var round = fixture.pendingAcceptance();

        assertThatThrownBy(() -> service.complete(
                round.taskId(), round.id(), round.version(), "qa-1"))
                .isInstanceOf(GovernanceValidationException.class)
                .hasMessage("固定验收样本尚未全部检查");
    }
}
