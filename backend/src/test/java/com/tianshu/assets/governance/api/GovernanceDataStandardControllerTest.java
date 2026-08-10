package com.tianshu.assets.governance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceDataStandardStore;
import com.tianshu.assets.governance.standard.application.GovernanceDataStandardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class GovernanceDataStandardControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var service = new GovernanceDataStandardService(
                new InMemoryGovernanceDataStandardStore(), types -> java.util.List.of(101L, 102L));
        mockMvc = standaloneSetup(new GovernanceDataStandardController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void createsImmutableVersionAndEnablesItWithImpactReview() throws Exception {
        var body = """
                {"standardVersion":2,"name":"数模资产完整性标准",
                 "applicableAssetTypes":["THREE_DIMENSIONAL_MODEL"],
                 "ownerUserId":"emp-li","ownerName":"李娜",
                 "changeSummary":"增加文件角色要求",
                 "rules":[{"targetField":"fileRole","ruleType":"FILE_ROLE",
                   "description":"必须明确主文件","blocking":true,"configurationJson":"{}"}]}
                """;
        var response = mockMvc.perform(post("/api/v1/governance/standards/1/versions")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.standardVersion").value(2))
                .andReturn().getResponse().getContentAsString();
        var id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asLong();

        mockMvc.perform(post("/api/v1/governance/standards/{id}/enable", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.standard.status").value("ENABLED"))
                .andExpect(jsonPath("$.standard.affectedAssetCount").value(2))
                .andExpect(jsonPath("$.impactReview.status").value("OPEN"))
                .andExpect(jsonPath("$.impactReview.assetIds[1]").value(102));

        mockMvc.perform(get("/api/v1/governance/standards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].standardVersion").value(2))
                .andExpect(jsonPath("$[1].status").value("DISABLED"));
    }

    @Test
    void rejectsDuplicateVersionAndStaleActivation() throws Exception {
        var duplicate = """
                {"standardCode":"FIELD-COMPLETENESS","standardVersion":1,
                 "name":"重复版本","ownerUserId":"emp-li","ownerName":"李娜",
                 "changeSummary":"重复","rules":[]}
                """;
        mockMvc.perform(post("/api/v1/governance/standards")
                        .contentType(MediaType.APPLICATION_JSON).content(duplicate))
                .andExpect(status().isConflict());

        var draft = mockMvc.perform(post("/api/v1/governance/standards/1/versions")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"standardVersion":2,"name":"并发测试标准",
                                 "ownerUserId":"emp-li","ownerName":"李娜",
                                 "changeSummary":"并发测试",
                                 "rules":[{"targetField":"scope","ruleType":"REQUIRED",
                                   "description":"范围必填","blocking":true,"configurationJson":"{}"}]}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var draftId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(draft).get("id").asLong();
        mockMvc.perform(post("/api/v1/governance/standards/{id}/enable", draftId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":9}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("governance_version_conflict"));
    }

    @Test
    void disabledStandardCannotRemainAnEnabledSource() throws Exception {
        mockMvc.perform(post("/api/v1/governance/standards/1/disable")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        mockMvc.perform(get("/api/v1/governance/standards/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
    }
}
