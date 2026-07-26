package com.tianshu.assets.governance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.dictionary.infrastructure.InMemoryDictionaryStore;
import com.tianshu.assets.governance.execution.application.FieldSupplementActionHandler;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionService;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceEmployeeDirectory;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceExecutionStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceRuleCatalog;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import com.tianshu.assets.governance.support.GovernanceTestFixture;
import com.tianshu.assets.governance.task.domain.GovernanceRuleSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class GovernanceExecutionControllerTest {

    private MockMvc mockMvc;
    private long taskId;
    private long itemId;
    private long assetVersion;

    @BeforeEach
    void setUp() {
        var fixture = GovernanceTestFixture.fieldClosure();
        var task = fixture.validDraft();
        var started = fixture.startService().start(task.id(), task.version(), "emp-admin");
        var rules = new GovernanceRuleSnapshot(
                0, "FIELD-COMPLETENESS", 3, 3,
                Map.of("specialty", 5L), "FIELD-QUALITY", 2);
        var executionStore = new InMemoryGovernanceExecutionStore(fixture.workflowStore());
        var service = new GovernanceExecutionService(
                executionStore, fixture.workflowStore(), new InMemoryGovernanceRuleCatalog(rules),
                new InMemoryAssetRepository(),
                new FieldSupplementActionHandler(
                        new ObjectMapper(), new InMemoryDictionaryStore(),
                        new InMemoryGovernanceEmployeeDirectory()),
                Clock.fixed(Instant.parse("2026-07-26T06:00:00Z"), ZoneOffset.UTC));
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
}
