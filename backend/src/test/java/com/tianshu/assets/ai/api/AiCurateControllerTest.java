package com.tianshu.assets.ai.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.ai.application.AiCurateService;
import com.tianshu.assets.ai.application.AiSuggestionService;
import com.tianshu.assets.ai.infrastructure.FakeAiCapabilityClient;
import com.tianshu.assets.ai.infrastructure.InMemoryAiSuggestionRepository;
import com.tianshu.assets.asset.application.AssetWriteService;
import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.document.infrastructure.InMemoryDocumentRepository;
import com.tianshu.assets.system.infrastructure.InMemoryOperationLogStore;
import com.tianshu.assets.system.infrastructure.InMemorySystemUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import com.tianshu.assets.asset.infrastructure.InMemoryAssetCollaborationStore;

class AiCurateControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var assets = new InMemoryAssetRepository();
        var capability = new FakeAiCapabilityClient();
        var users = new InMemorySystemUserRepository();
        var logs = new InMemoryOperationLogStore();
        var suggestions = new InMemoryAiSuggestionRepository();
        var suggestionService = new AiSuggestionService(suggestions, users, new AssetWriteService(assets, new InMemoryAssetCollaborationStore(), new InMemoryOperationLogStore()), logs);
        var curate = new AiCurateService(capability, suggestionService, assets,
                new InMemoryDocumentRepository(), users, "ep-docs");
        mockMvc = standaloneSetup(new AiCurateController(curate))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void regenerateByContentAdminCreatesPendingSuggestion() throws Exception {
        mockMvc.perform(post("/api/v1/ai/targets/ASSET/103/suggestions/regenerate")
                        .header("X-User-Id", "emp-admin")
                        .header("X-User-Roles", "CONTENT_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.targetId").value(103));
    }

    @Test
    void regenerateWithoutRoleIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/ai/targets/ASSET/103/suggestions/regenerate")
                        .header("X-User-Id", "emp-admin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("operation_forbidden"));
    }

    @Test
    void regenerateOutOfScopeIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/ai/targets/ASSET/105/suggestions/regenerate")
                        .header("X-User-Id", "emp-chen")
                        .header("X-User-Roles", "CONTENT_ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ai_suggestion_scope_forbidden"));
    }

    @Test
    void regenerateUnknownAssetIsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/ai/targets/ASSET/9999/suggestions/regenerate")
                        .header("X-User-Id", "emp-admin")
                        .header("X-User-Roles", "CONTENT_ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("asset_not_found"));
    }
}
