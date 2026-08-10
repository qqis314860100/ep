package com.tianshu.assets.governance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.dictionary.infrastructure.InMemoryDictionaryStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceDataStandardStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceMappingRuleStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceRuleCatalog;
import com.tianshu.assets.governance.mapping.application.GovernanceMappingRuleService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class GovernanceMappingRuleControllerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var standardStore = new InMemoryGovernanceDataStandardStore();
        var scope = new AssetScope("乘用车", "H03", "宁德基地", "A 拉线", "焊接段", "乘用车", "底部水冷");
        var catalog = new InMemoryGovernanceRuleCatalog(
                new com.tianshu.assets.governance.task.domain.GovernanceRuleSnapshot(0, "FIELD-COMPLETENESS", 1, 1, java.util.Map.of(), "QUALITY", 1), List.of(scope));
        var service = new GovernanceMappingRuleService(new InMemoryGovernanceMappingRuleStore(),
                new InMemoryDictionaryStore(), standardStore, catalog);
        mockMvc = standaloneSetup(new GovernanceMappingRuleController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private String body(boolean ambiguous) {
        return """
                {"standardId":1,"sourceDimension":"平台文本","sourceValue":"H03底部水冷",
                 "targetDictionaryCategory":"PLATFORM_VARIANT","targetDictionaryItemId":12,
                 "scope":{"platform":"乘用车","productLine":"H03","base":"宁德基地","productionLine":"A 拉线","processSection":"焊接段","platformFamily":"乘用车","platformVariant":"底部水冷"},
                 "ambiguous":%s,"affectedAssetCount":4}
                """.formatted(ambiguous);
    }

    @Test
    void createsConfirmsAndDisablesMappingWithHistory() throws Exception {
        var created = mockMvc.perform(post("/api/v1/governance/mappings").contentType(MediaType.APPLICATION_JSON).content(body(false)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"))
                .andReturn().getResponse().getContentAsString();
        var id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).get("id").asLong();
        mockMvc.perform(post("/api/v1/governance/mappings/{id}/confirm", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"userId\":\"emp-li\",\"userName\":\"李娜\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.confirmedByUserId").value("emp-li"));
        mockMvc.perform(post("/api/v1/governance/mappings/{id}/disable", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
        mockMvc.perform(get("/api/v1/governance/mappings/{id}/history", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
    }

    @Test
    void requiresCommentForAmbiguousMapping() throws Exception {
        var created = mockMvc.perform(post("/api/v1/governance/mappings").contentType(MediaType.APPLICATION_JSON).content(body(true)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).get("id").asLong();
        mockMvc.perform(post("/api/v1/governance/mappings/{id}/confirm", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"userId\":\"emp-li\",\"userName\":\"李娜\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("governance_validation_failed"));
    }

    @Test
    void rejectsCrossScopeAndStaleConfirmation() throws Exception {
        var cross = body(false).replace("\"platformVariant\":\"底部水冷\"", "\"platformVariant\":\"大面水冷\"");
        mockMvc.perform(post("/api/v1/governance/mappings").contentType(MediaType.APPLICATION_JSON).content(cross))
                .andExpect(status().isUnprocessableEntity());
        var created = mockMvc.perform(post("/api/v1/governance/mappings").contentType(MediaType.APPLICATION_JSON).content(body(false)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).get("id").asLong();
        mockMvc.perform(post("/api/v1/governance/mappings/{id}/confirm", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":9,\"userId\":\"emp-li\",\"userName\":\"李娜\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("governance_version_conflict"));
    }
}
