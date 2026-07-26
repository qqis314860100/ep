package com.tianshu.assets.governance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionService;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import com.tianshu.assets.governance.support.GovernanceTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class GovernanceExecutionControllerTest {

    private MockMvc mockMvc;
    private long taskId;
    private long itemId;
    private long secondItemId;
    private long assetVersion;
    private long secondAssetVersion;

    @BeforeEach
    void setUp() {
        var fixture = GovernanceTestFixture.batchFieldClosure();
        var started = fixture.validStartedTask();
        var service = fixture.executionService();
        mockMvc = standaloneSetup(new GovernanceExecutionController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        taskId = started.id();
        var item = service.items(taskId).stream()
                .map(GovernanceExecutionService.ItemExecutionContext::item)
                .filter(candidate -> candidate.targetField() == GovernanceField.DESCRIPTION)
                .findFirst().orElseThrow();
        itemId = item.id();
        assetVersion = item.assetVersion();
        var second = service.items(taskId).get(1).item();
        secondItemId = second.id();
        secondAssetVersion = second.assetVersion();
    }

    @Test
    void listsExecutionContextSavesStructuredDraftAndSubmitsIt() throws Exception {
        mockMvc.perform(get("/api/v1/governance/tasks/{taskId}/items", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].item.targetField").value("DESCRIPTION"))
                .andExpect(jsonPath("$[0].originalFactJson").value("\"\""))
                .andExpect(jsonPath("$[0].ruleContext.dataStandardVersion").value(3))
                .andExpect(jsonPath("$[0].item.version").value(0));

        var saved = mockMvc.perform(put("/api/v1/governance/items/{itemId}/result-draft", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemVersion\":0,\"assetVersion\":" + assetVersion
                                + ",\"proposedValue\":{\"description\":\"标准说明\"},"
                                + "\"actorUserId\":\"emp-chen\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.proposedValue.description").value("标准说明"))
                .andReturn().getResponse().getContentAsString();
        var result = new ObjectMapper().readTree(saved);

        mockMvc.perform(get("/api/v1/governance/tasks/{taskId}/items", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentResult.proposedValue.description").value("标准说明"))
                .andExpect(jsonPath("$[0].currentResult.proposedValueJson").doesNotExist());

        mockMvc.perform(post("/api/v1/governance/items/{itemId}/submit", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultVersionId\":" + result.get("id").asLong()
                                + ",\"resultVersion\":" + result.get("version").asLong()
                                + ",\"actorUserId\":\"emp-chen\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submittedAt").isNotEmpty());
    }

    @Test
    void mapsOptimisticConflictToStableGovernanceVersionCode() throws Exception {
        mockMvc.perform(put("/api/v1/governance/items/{itemId}/result-draft", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemVersion\":1,\"assetVersion\":" + assetVersion
                                + ",\"proposedValue\":{\"description\":\"标准说明\"},"
                                + "\"actorUserId\":\"emp-chen\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("governance_version_conflict"));
    }

    @Test
    void rejectsNullStructuredValueAtBoundary() throws Exception {
        mockMvc.perform(put("/api/v1/governance/items/{itemId}/result-draft", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemVersion\":0,\"assetVersion\":" + assetVersion
                                + ",\"proposedValue\":null,\"actorUserId\":\"emp-chen\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }

    @Test
    void executesBatchAndReturnsIndependentItemOutcomes() throws Exception {
        mockMvc.perform(post("/api/v1/governance/results/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"api-batch-001\",\"commands\":["
                                + "{\"itemId\":" + itemId + ",\"itemVersion\":0,\"assetVersion\":" + assetVersion
                                + ",\"targetField\":\"DESCRIPTION\",\"standardVersion\":3,\"scopeFingerprint\":\"scope-a\","
                                + "\"proposedValue\":{\"description\":\"标准说明\"},\"actorUserId\":\"emp-chen\"},"
                                + "{\"itemId\":" + secondItemId + ",\"itemVersion\":0,\"assetVersion\":" + secondAssetVersion
                                + ",\"targetField\":\"DESCRIPTION\",\"standardVersion\":3,\"scopeFingerprint\":\"scope-a\","
                                + "\"proposedValue\":{\"description\":\" \"},\"actorUserId\":\"emp-chen\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].outcome").value("SUCCESS"))
                .andExpect(jsonPath("$.results[1].outcome").value("VALIDATION_FAILED"));
    }

    @Test
    void mapsNullBatchElementToIndependentValidationFailure() throws Exception {
        mockMvc.perform(post("/api/v1/governance/results/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"api-null-item\",\"commands\":[null,"
                                + "{\"itemId\":" + itemId + ",\"itemVersion\":0,\"assetVersion\":" + assetVersion
                                + ",\"targetField\":\"DESCRIPTION\",\"standardVersion\":3,\"scopeFingerprint\":\"scope-a\","
                                + "\"proposedValue\":{\"description\":\"标准说明\"},\"actorUserId\":\"emp-chen\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].outcome").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.results[1].outcome").value("SUCCESS"));
    }
}
