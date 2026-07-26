package com.tianshu.assets.governance.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.tianshu.assets.governance.support.GovernanceTestFixture;
import org.junit.jupiter.api.Test;

class GovernanceReportServiceTest {

    @Test
    void reportKeepsOriginalProposedDecisionAcceptanceAndApplyFacts() {
        var fixture = GovernanceTestFixture.reportFieldClosure();
        var completedTask = fixture.completedFieldTask();
        var service = fixture.reportService();

        var report = service.report(completedTask.id());

        assertThat(report.rounds()).hasSize(2);
        assertThat(report.items().getFirst().originalValueJson()).contains("旧说明");
        assertThat(report.items().getFirst().appliedValueJson()).contains("标准说明");
        assertThat(report.items().getFirst().confirmationDecisions()).hasSize(2);
        assertThat(report.applicationSummary().failed()).isZero();
    }

    @Test
    void actualGovernanceCommandsProduceHistoryEvents() {
        var fixture = GovernanceTestFixture.reportFieldClosure();

        var task = fixture.completedFieldTask();

        assertThat(fixture.auditService().history(task.id()))
                .extracting(event -> event.action())
                .contains("TASK_STARTED", "RESULT_SUBMITTED", "CONFIRMATION_COMPLETED",
                        "REWORK_OPENED", "ACCEPTANCE_COMPLETED", "APPLICATION_SUCCEEDED",
                        "TASK_COMPLETED");
    }
}
