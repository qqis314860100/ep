package com.tianshu.assets.governance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceIssueStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceTaskStore;
import com.tianshu.assets.governance.issue.application.GovernanceIssueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class GovernanceIssueControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var service = new GovernanceIssueService(
                InMemoryGovernanceIssueStore.withFieldSeeds(), new InMemoryGovernanceTaskStore());
        mockMvc = standaloneSetup(new GovernanceIssueController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void listsSafeIssueFactsWithOptionalFilters() throws Exception {
        mockMvc.perform(get("/api/v1/governance/issues")
                        .param("field", "DESCRIPTION")
                        .param("status", "OPEN")
                        .param("assetId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1001))
                .andExpect(jsonPath("$[0].assetId").value(101))
                .andExpect(jsonPath("$[0].targetField").value("DESCRIPTION"))
                .andExpect(jsonPath("$[0].issueType").value("MISSING_DESCRIPTION"))
                .andExpect(jsonPath("$[0].targetPath").value("/description"))
                .andExpect(jsonPath("$[0].originalFactJson").isString())
                .andExpect(jsonPath("$[0].severity").value("HIGH"))
                .andExpect(jsonPath("$[0].blocking").value(true))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].taskId").doesNotExist())
                .andExpect(jsonPath("$[0].version").value(0));
    }

    @Test
    void rejectsInvalidEnumFilterAtBoundary() throws Exception {
        mockMvc.perform(get("/api/v1/governance/issues").param("field", "UNKNOWN"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("invalid_request"));
    }
}
