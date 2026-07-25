package com.tianshu.assets.governance.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tianshu.assets.governance.execution.domain.GovernanceItemStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class GovernanceWorkflowTest {

    @Test
    void rejectsSkippingBusinessConfirmation() {
        assertThatThrownBy(() -> GovernanceTaskStatus.IN_PROGRESS.moveTo(GovernanceTaskStatus.PENDING_ACCEPTANCE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("治理任务状态不能从 IN_PROGRESS 跳转到 PENDING_ACCEPTANCE");
    }

    @Test
    void aggregatesProgressFromItemStates() {
        var progress = GovernanceProgress.from(List.of(
                GovernanceItemStatus.SUBMITTED,
                GovernanceItemStatus.CONFIRMED,
                GovernanceItemStatus.ACCEPTED,
                GovernanceItemStatus.BLOCKED,
                GovernanceItemStatus.REWORK_REQUIRED));

        assertThat(progress.total()).isEqualTo(5);
        assertThat(progress.submitted()).isEqualTo(3);
        assertThat(progress.confirmed()).isEqualTo(2);
        assertThat(progress.accepted()).isEqualTo(1);
        assertThat(progress.blocked()).isEqualTo(1);
        assertThat(progress.reworkRequired()).isEqualTo(1);
    }
}
