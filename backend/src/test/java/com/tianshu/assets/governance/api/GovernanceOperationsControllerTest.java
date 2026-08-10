package com.tianshu.assets.governance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceAcceptanceStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceConfirmationStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceDataStandardStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceEmployeeDirectory;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceIssueStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceScanRunStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceTaskStore;
import com.tianshu.assets.governance.operations.application.GovernanceOperationsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class GovernanceOperationsControllerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new GovernanceOperationsController(new GovernanceOperationsService(
                new InMemoryAssetRepository(), InMemoryGovernanceIssueStore.withFieldSeeds(),
                InMemoryGovernanceTaskStore.withLegacySeed(), new InMemoryGovernanceConfirmationStore(),
                new InMemoryGovernanceAcceptanceStore(), new InMemoryGovernanceScanRunStore(),
                new InMemoryGovernanceEmployeeDirectory(), new InMemoryGovernanceDataStandardStore())))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void returnsOverviewWithFilterEchoAndCadence() throws Exception {
        mockMvc.perform(get("/api/v1/governance/operations/overview")
                        .param("issueType", "MISSING_DESCRIPTION")
                        .param("fromDate", "2026-08-01")
                        .param("toDate", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filter.issueType").value("MISSING_DESCRIPTION"))
                .andExpect(jsonPath("$.assetCount").value(5))
                .andExpect(jsonPath("$.metrics.length()").value(8))
                .andExpect(jsonPath("$.cadences.length()").value(4));
    }
}
