package com.tianshu.assets.ai.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.tianshu.assets.ai.application.AiSuggestionService;
import com.tianshu.assets.ai.domain.AiProposedFields;
import com.tianshu.assets.ai.domain.AiSuggestionStatus;
import com.tianshu.assets.ai.domain.AiSuggestionTargetType;
import com.tianshu.assets.ai.domain.AiTargetScope;
import com.tianshu.assets.ai.infrastructure.InMemoryAiSuggestionRepository;
import com.tianshu.assets.asset.application.AssetWriteService;
import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import com.tianshu.assets.common.api.ApiExceptionHandler;
import com.tianshu.assets.system.domain.OperationLogCriteria;
import com.tianshu.assets.system.infrastructure.InMemoryOperationLogStore;
import com.tianshu.assets.system.infrastructure.InMemorySystemUserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

class AiSuggestionControllerTest {

    private static final String ADMIN = "emp-admin";
    private static final String ADMIN_ROLES = "SYSTEM_ADMIN,CONTENT_ADMIN";
    private static final String SCOPE_USER = "emp-chen";

    private MockMvc mockMvc;
    private InMemoryAssetRepository assetRepository;
    private AiSuggestionService service;
    private InMemoryOperationLogStore logs;

    @BeforeEach
    void setUp() {
        assetRepository = new InMemoryAssetRepository();
        var assetWrite = new AssetWriteService(assetRepository);
        var suggestions = new InMemoryAiSuggestionRepository();
        var users = new InMemorySystemUserRepository();
        logs = new InMemoryOperationLogStore();
        service = new AiSuggestionService(suggestions, users, assetWrite, logs);
        mockMvc = standaloneSetup(new AiSuggestionController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private long registerForAsset(long assetId, AiProposedFields proposed) {
        var asset = assetRepository.findById(assetId).orElseThrow();
        return service.register(new AiSuggestionService.AiSuggestionDraft(
                AiSuggestionTargetType.ASSET, assetId, asset.name(), proposed, List.of("证据片段"), 0.9,
                asset.scopes().stream().map(AiTargetScope::fromAssetScope).toList(), "ai-service")).id();
    }

    @Test
    void listReturnsOnlySuggestionsWithinCallerScope() throws Exception {
        registerForAsset(103, new AiProposedFields("宁德建议", "", null, List.of(), null, null));
        registerForAsset(105, new AiProposedFields("溧阳建议", "", null, List.of(), null, null));

        mockMvc.perform(get("/api/v1/ai/suggestions").header("X-User-Id", SCOPE_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].targetId").value(103))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].source").value("AI"));

        mockMvc.perform(get("/api/v1/ai/suggestions").header("X-User-Id", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(2));
    }

    @Test
    void listFiltersByTargetAndStatus() throws Exception {
        var confirmed = registerForAsset(103, new AiProposedFields("命名", "", null, List.of(), null, null));
        service.confirm(confirmed, ADMIN, "管理员", ADMIN_ROLES);
        registerForAsset(105, new AiProposedFields("另一建议", "", null, List.of(), null, null));

        mockMvc.perform(get("/api/v1/ai/suggestions")
                        .header("X-User-Id", ADMIN)
                        .param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].status").value("CONFIRMED"));

        mockMvc.perform(get("/api/v1/ai/suggestions")
                        .header("X-User-Id", ADMIN)
                        .param("target_type", "ASSET")
                        .param("target_id", "105"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].targetId").value(105));
    }

    @Test
    void confirmAppliesCuratedFieldsReturnsConfirmedAndAudits() throws Exception {
        var suggestionId = registerForAsset(103,
                new AiProposedFields("输送模块布置数模（AI 整理）", "AI 描述", "THREE_DIMENSIONAL_MODEL",
                        List.of("输送"), null, null));

        mockMvc.perform(post("/api/v1/ai/suggestions/{id}/confirm", suggestionId)
                        .header("X-User-Id", ADMIN)
                        .header("X-User-Name", "管理员")
                        .header("X-User-Roles", ADMIN_ROLES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.resolvedBy").value(ADMIN))
                .andExpect(jsonPath("$.source").value("AI"));

        var asset = assetRepository.findById(103).orElseThrow();
        assertThat(asset.name()).isEqualTo("输送模块布置数模（AI 整理）");
        assertThat(asset.assetType().name()).isEqualTo("THREE_DIMENSIONAL_MODEL");
        assertThat(asset.tags()).contains("输送");
        assertThat(logs.count(new OperationLogCriteria("", "AI_SUGGESTION_CONFIRMED", "ASSET",
                null, null, 1, 20))).isEqualTo(1);
    }

    @Test
    void confirmOutOfScopeIsForbidden() throws Exception {
        var suggestionId = registerForAsset(105, new AiProposedFields("改名", "", null, List.of(), null, null));

        mockMvc.perform(post("/api/v1/ai/suggestions/{id}/confirm", suggestionId)
                        .header("X-User-Id", SCOPE_USER)
                        .header("X-User-Roles", "CONTENT_ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ai_suggestion_scope_forbidden"));
    }

    @Test
    void confirmWithoutCurationRoleIsForbidden() throws Exception {
        var suggestionId = registerForAsset(103, new AiProposedFields("改名", "", null, List.of(), null, null));

        mockMvc.perform(post("/api/v1/ai/suggestions/{id}/confirm", suggestionId)
                        .header("X-User-Id", SCOPE_USER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("operation_forbidden"));
    }

    @Test
    void listAndConfirmAreEmptyForUnknownUser() throws Exception {
        var suggestionId = registerForAsset(103, new AiProposedFields("改名", "", null, List.of(), null, null));

        mockMvc.perform(get("/api/v1/ai/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(0));

        mockMvc.perform(post("/api/v1/ai/suggestions/{id}/confirm", suggestionId)
                        .header("X-User-Roles", ADMIN_ROLES))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ai_suggestion_scope_forbidden"));
    }

    @Test
    void rejectMarksSuggestionRejected() throws Exception {
        var suggestionId = registerForAsset(103, new AiProposedFields("命名", "", null, List.of(), null, null));

        mockMvc.perform(post("/api/v1/ai/suggestions/{id}/reject", suggestionId)
                        .header("X-User-Id", ADMIN)
                        .header("X-User-Roles", ADMIN_ROLES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void confirmTwiceIsConflict() throws Exception {
        var suggestionId = registerForAsset(103, new AiProposedFields("命名", "", null, List.of(), null, null));

        mockMvc.perform(post("/api/v1/ai/suggestions/{id}/confirm", suggestionId)
                        .header("X-User-Id", ADMIN)
                        .header("X-User-Roles", ADMIN_ROLES))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ai/suggestions/{id}/confirm", suggestionId)
                        .header("X-User-Id", ADMIN)
                        .header("X-User-Roles", ADMIN_ROLES))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ai_suggestion_state_conflict"));
    }

    @Test
    void confirmOnStandardizedAssetIsRejectedAndSuggestionReverts() throws Exception {
        var suggestionId = registerForAsset(101, new AiProposedFields("改名", "", null, List.of(), null, null));

        mockMvc.perform(post("/api/v1/ai/suggestions/{id}/confirm", suggestionId)
                        .header("X-User-Id", ADMIN)
                        .header("X-User-Roles", ADMIN_ROLES))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("invalid_request"));

        mockMvc.perform(get("/api/v1/ai/suggestions")
                        .header("X-User-Id", ADMIN)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void confirmUnknownSuggestionIsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/ai/suggestions/9999/confirm")
                        .header("X-User-Id", ADMIN)
                        .header("X-User-Roles", ADMIN_ROLES))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ai_suggestion_not_found"));
    }
}
