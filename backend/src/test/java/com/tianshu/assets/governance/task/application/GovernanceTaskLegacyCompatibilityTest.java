package com.tianshu.assets.governance.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tianshu.assets.governance.application.GovernanceTaskStateException;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceTaskStore;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import com.tianshu.assets.governance.task.domain.GovernanceWorkflowVersion;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GovernanceTaskLegacyCompatibilityTest {

    @Test
    void legacyProgressTaskIsVisibleButCannotEnterClosedLoopCommands() {
        var store = InMemoryGovernanceTaskStore.withLegacySeed();
        var service = new GovernanceTaskApplicationService(store);

        var task = service.get(1L);

        assertThat(task.workflowVersion()).isEqualTo(GovernanceWorkflowVersion.LEGACY_PROGRESS);
        assertThat(task.legacyTotal()).isEqualTo(286);
        assertThatThrownBy(() -> service.requireClosedLoop(1L))
                .isInstanceOf(GovernanceTaskStateException.class)
                .hasMessage("历史进度任务为只读，请按问题池重新建单");
    }

    @Test
    void legacyProgressTaskMayHaveNoDueDate() {
        var task = taskWith(null, GovernanceWorkflowVersion.LEGACY_PROGRESS);

        assertThat(task.dueDate()).isNull();
    }

    @Test
    void closedLoopTaskRequiresDueDate() {
        assertThatThrownBy(() -> taskWith(null, GovernanceWorkflowVersion.CLOSED_LOOP_V1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("计划完成日期不能为空");
    }

    private GovernanceTask taskWith(LocalDate dueDate, GovernanceWorkflowVersion workflowVersion) {
        return new GovernanceTask(10, "GOV-10", "字段治理", "NORMALIZE", "MISSING_FIELD",
                "emp-chen", "陈工", "emp-chen", dueDate, GovernanceTaskStatus.DRAFT, 0,
                workflowVersion, null, null, 0, 0, 0);
    }
}
