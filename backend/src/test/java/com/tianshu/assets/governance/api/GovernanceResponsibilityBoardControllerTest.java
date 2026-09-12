package com.tianshu.assets.governance.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceAcceptanceStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceEmployeeDirectory;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceIssueStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceScanRunStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceTaskStore;
import com.tianshu.assets.governance.responsibility.application.GovernanceResponsibilityService;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import com.tianshu.assets.governance.task.domain.GovernanceWorkflowVersion;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class GovernanceResponsibilityBoardControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var taskStore = new InMemoryGovernanceTaskStore();
        taskStore.insert(new GovernanceTask(1, "GOV-R4", "看板任务", "FIELD_SUPPLEMENT", "FIELD_COMPLETENESS",
                "emp-li", "李工", "emp-li", LocalDate.now().minusDays(1),
                GovernanceTaskStatus.PENDING_CONFIRMATION, 1,
                GovernanceWorkflowVersion.CLOSED_LOOP_V1, null, null, 0, 0, 0));
        var service = new GovernanceResponsibilityService(
                taskStore,
                new InMemoryGovernanceEmployeeDirectory(),
                new InMemoryGovernanceIssueStore(),
                new InMemoryGovernanceScanRunStore(),
                new InMemoryGovernanceAcceptanceStore());
        mockMvc = GovernanceApiTestSupport.adminFor(new GovernanceResponsibilityBoardController(
                service, GovernanceApiTestSupport.authorization()));
    }

    @Test
    void exposesBoardWithEmployeeBucketsTotalsAndMonth() throws Exception {
        mockMvc.perform(get("/api/v1/governance/responsibility/board"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").isNotEmpty())
                .andExpect(jsonPath("$.employees[*].name", hasItem("李工")))
                .andExpect(jsonPath("$.employees[?(@.name=='李工')].confirming", hasItem(1)))
                .andExpect(jsonPath("$.employees[?(@.name=='李工')].overdue", hasItem(1)))
                .andExpect(jsonPath("$.totals.confirming").value(1))
                .andExpect(jsonPath("$.monthly.openIssues").isNumber());
    }

    @Test
    void rejectsMalformedMonthWithValidationEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/governance/responsibility/board").param("month", "2026-13"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("governance_validation_failed"));
    }
}
