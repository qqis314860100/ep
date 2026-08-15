package com.tianshu.assets.governance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceIssueStore;
import com.tianshu.assets.governance.inventory.application.AssetInventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class GovernanceInventoryControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var service = new AssetInventoryService(
                new InMemoryAssetRepository(), InMemoryGovernanceIssueStore.withFieldSeeds());
        mockMvc = standaloneSetup(new GovernanceInventoryController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void reportsTotalsRatesAndFiltersByMissingFields() throws Exception {
        mockMvc.perform(get("/api/v1/governance/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.total").value(5))
                .andExpect(jsonPath("$.totals.pendingCuration").value(2))
                .andExpect(jsonPath("$.totals.standardized").value(3))
                .andExpect(jsonPath("$.rates.completeness").value(100.0));

        mockMvc.perform(get("/api/v1/governance/inventory").param("missing_base", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.total").value(2))
                .andExpect(jsonPath("$.items[0].assetNumber").value("LEGACY-00000104"));

        mockMvc.perform(get("/api/v1/governance/inventory").param("owner", "陈工"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.total").value(1))
                .andExpect(jsonPath("$.items[0].assetNumber").value("DM-ND-A-0001"));
    }
}
