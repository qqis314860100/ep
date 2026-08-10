package com.tianshu.assets.governance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.dictionary.infrastructure.InMemoryDictionaryStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceDataStandardStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceEmployeeDirectory;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceIssueStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceMappingRuleStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceRuleCatalog;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceScanRunStore;
import com.tianshu.assets.governance.scan.application.GovernanceScanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class GovernanceScanControllerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var standards = new InMemoryGovernanceDataStandardStore();
        var service = new GovernanceScanService(new InMemoryAssetRepository(), new InMemoryGovernanceIssueStore(), new InMemoryGovernanceScanRunStore(), standards,
                new InMemoryGovernanceMappingRuleStore(), new InMemoryDictionaryStore(), new InMemoryGovernanceEmployeeDirectory(), new InMemoryGovernanceRuleCatalog(standards), new ObjectMapper());
        mockMvc = standaloneSetup(new GovernanceScanController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void triggersScanAndListsRunHistory() throws Exception {
        mockMvc.perform(post("/api/v1/governance/scans"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("SUCCEEDED")).andExpect(jsonPath("$.scannedAssetCount").value(5));
        mockMvc.perform(get("/api/v1/governance/scans")).andExpect(status().isOk()).andExpect(jsonPath("$[0].triggerType").value("MANUAL"));
    }
}
