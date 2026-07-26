package com.tianshu.assets.governance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.common.api.WebConfiguration;
import com.tianshu.assets.governance.application.GovernanceAuthorizationService;
import com.tianshu.assets.governance.support.GovernanceTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.filter.CorsFilter;

class GovernanceClosedLoopApiTest {

    private MockMvc mockMvc;
    private long taskId;
    private long itemId;
    private long assetVersion;

    @BeforeEach
    void setUp() {
        var fixture = GovernanceTestFixture.batchFieldClosure();
        var task = fixture.validStartedTask();
        taskId = task.id();
        var executionService = fixture.executionService();
        var item = executionService.items(task.id()).getFirst().item();
        itemId = item.id();
        assetVersion = item.assetVersion();
        var authorizationService = new GovernanceAuthorizationService(fixture.executionStore());
        mockMvc = standaloneSetup(new GovernanceExecutionController(
                        executionService, new com.fasterxml.jackson.databind.ObjectMapper(), authorizationService))
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new CorsFilter(new WebConfiguration().corsConfigurationSource()))
                .build();
    }

    @Test
    void returnsConflictForStaleItemVersion() throws Exception {
        mockMvc.perform(put("/api/v1/governance/items/{itemId}/result-draft", itemId)
                        .header("X-User-Id", "emp-chen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemVersion\":1,\"assetVersion\":" + assetVersion
                                + ",\"proposedValue\":{\"description\":\"标准说明\"},"
                                + "\"actorUserId\":\"emp-chen\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("governance_version_conflict"));
    }

    @Test
    void rejectsExecutionByUnassignedUser() throws Exception {
        mockMvc.perform(put("/api/v1/governance/items/{itemId}/result-draft", itemId)
                        .header("X-User-Id", "other-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemVersion\":0,\"assetVersion\":" + assetVersion
                                + ",\"proposedValue\":{\"description\":\"标准说明\"},"
                                + "\"actorUserId\":\"other-user\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("governance_forbidden"));
    }

    @Test
    void rejectsExecutionQueryWithoutLeakingOriginalValue() throws Exception {
        mockMvc.perform(get("/api/v1/governance/tasks/{taskId}/items", taskId)
                        .header("X-User-Id", "other-user"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("governance_forbidden"))
                .andExpect(jsonPath("$[0].originalFactJson").doesNotExist());
    }

    @Test
    void corsAllowsPutForGovernanceCommands() throws Exception {
        mockMvc.perform(options("/api/v1/governance/items/{itemId}/result-draft", itemId)
                        .header("Origin", "http://127.0.0.1:5174")
                        .header("Access-Control-Request-Method", "PUT"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("PUT")));
    }
}
