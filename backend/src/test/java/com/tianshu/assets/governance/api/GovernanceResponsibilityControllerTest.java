package com.tianshu.assets.governance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.common.api.WebConfiguration;
import com.tianshu.assets.governance.application.GovernanceAuthorizationService;
import com.tianshu.assets.governance.application.GovernanceResponsibilityService;
import com.tianshu.assets.governance.infrastructure.InMemoryAssetResponsibilityAdapter;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceEmployeeDirectory;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceExecutionStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceWorkflowStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.filter.CorsFilter;

class GovernanceResponsibilityControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var responsibilityAdapter = new InMemoryAssetResponsibilityAdapter();
        var service = new GovernanceResponsibilityService(
                responsibilityAdapter,
                new InMemoryAssetRepository(),
                new InMemoryGovernanceEmployeeDirectory());
        var authorization = new GovernanceAuthorizationService(
                new InMemoryGovernanceExecutionStore(new InMemoryGovernanceWorkflowStore()),
                responsibilityAdapter);
        mockMvc = standaloneSetup(new GovernanceResponsibilityController(service, authorization))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new CorsFilter(new WebConfiguration().corsConfigurationSource()))
                .build();
    }

    @Test
    void assignsResponsibilityWithContentAdmin() throws Exception {
        mockMvc.perform(put("/api/v1/governance/asset-responsibilities/101")
                        .header("X-User-Roles", "CONTENT_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsibleUserId\":\"emp-chen\",\"responsibilityScope\":\"设备工程部\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetId").value(101))
                .andExpect(jsonPath("$.responsibleUserId").value("emp-chen"))
                .andExpect(jsonPath("$.responsibilityScope").value("设备工程部"));
    }

    @Test
    void readsAssignedResponsibility() throws Exception {
        mockMvc.perform(put("/api/v1/governance/asset-responsibilities/101")
                        .header("X-User-Roles", "SYSTEM_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsibleUserId\":\"emp-li\",\"responsibilityScope\":\"标准化小组\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/governance/asset-responsibilities/101")
                        .header("X-User-Roles", "CONTENT_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responsibleUserId").value("emp-li"));
    }

    @Test
    void rejectsAssignmentWithoutAdminRole() throws Exception {
        mockMvc.perform(put("/api/v1/governance/asset-responsibilities/101")
                        .header("X-User-Roles", "UPLOADER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsibleUserId\":\"emp-chen\",\"responsibilityScope\":\"设备工程部\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("governance_forbidden"));
    }

    @Test
    void rejectsAssignmentForUnknownEmployee() throws Exception {
        mockMvc.perform(put("/api/v1/governance/asset-responsibilities/101")
                        .header("X-User-Roles", "CONTENT_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsibleUserId\":\"ghost-user\",\"responsibilityScope\":\"设备工程部\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("governance_validation_failed"));
    }

    @Test
    void rejectsAssignmentForUnknownAsset() throws Exception {
        mockMvc.perform(put("/api/v1/governance/asset-responsibilities/9999")
                        .header("X-User-Roles", "CONTENT_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsibleUserId\":\"emp-chen\",\"responsibilityScope\":\"设备工程部\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("governance_not_found"));
    }

    @Test
    void rejectsBlankResponsibleUser() throws Exception {
        mockMvc.perform(put("/api/v1/governance/asset-responsibilities/101")
                        .header("X-User-Roles", "CONTENT_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responsibleUserId\":\"\",\"responsibilityScope\":\"设备工程部\"}"))
                .andExpect(status().isBadRequest());
    }
}
