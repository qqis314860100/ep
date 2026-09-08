package com.tianshu.assets.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tianshu.assets.ai.domain.AiProposedFields;
import com.tianshu.assets.ai.domain.AiSuggestionStatus;
import com.tianshu.assets.ai.domain.AiSuggestionTargetType;
import com.tianshu.assets.ai.domain.AiTargetScope;
import com.tianshu.assets.ai.infrastructure.InMemoryAiSuggestionRepository;
import com.tianshu.assets.asset.application.AssetWriteService;
import com.tianshu.assets.asset.application.ForbiddenOperationException;
import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import com.tianshu.assets.system.domain.OperationLogCriteria;
import com.tianshu.assets.system.infrastructure.InMemoryOperationLogStore;
import com.tianshu.assets.system.infrastructure.InMemorySystemUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiSuggestionServiceTest {

    private static final String ADMIN = "emp-admin";
    private static final String CONTENT_ADMIN_ROLE = "CONTENT_ADMIN";
    private static final String CURATOR = "emp-chen";

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
        var clock = Clock.fixed(Instant.parse("2026-09-09T08:00:00Z"), ZoneOffset.UTC);
        service = new AiSuggestionService(suggestions, users, assetWrite, logs, clock);
    }

    private AiSuggestionService.AiSuggestionView registerForAsset(long assetId, AiProposedFields proposed) {
        var asset = assetRepository.findById(assetId).orElseThrow();
        return service.register(new AiSuggestionService.AiSuggestionDraft(
                AiSuggestionTargetType.ASSET,
                assetId,
                asset.name(),
                proposed,
                List.of("片段一：输送模块布置与接口空间说明"),
                0.92,
                asset.scopes().stream().map(AiTargetScope::fromAssetScope).toList(),
                "ai-service"));
    }

    private AiSuggestionService.AiSuggestionView pendingOf(long id) {
        return service.list(ADMIN, null, null, null, 1, 100).items().stream()
                .filter(item -> item.id() == id)
                .findFirst().orElseThrow();
    }

    @Test
    void registerSupersedesPendingSuggestionOfSameTarget() {
        var first = registerForAsset(103, new AiProposedFields("命名一", "", null, List.of(), null, null));
        var second = registerForAsset(103, new AiProposedFields("命名二", "", null, List.of(), null, null));
        assertThat(second.status()).isEqualTo(AiSuggestionStatus.PENDING);
        assertThat(pendingOf(first.id()).status()).isEqualTo(AiSuggestionStatus.SUPERSEDED);
    }

    @Test
    void registerRejectsKnowledgeDocumentTargetForNow() {
        assertThatThrownBy(() -> service.register(new AiSuggestionService.AiSuggestionDraft(
                AiSuggestionTargetType.KNOWLEDGE_DOC, 1L, "文档", new AiProposedFields("", "", null, List.of(), "", ""),
                List.of(), null, List.of(), "ai-service")))
                .isInstanceOf(AiSuggestionValidationException.class);
    }

    @Test
    void confirmAppliesCuratedFieldsToPendingCurationAssetAndAudits() {
        var suggestion = registerForAsset(103,
                new AiProposedFields("输送模块布置数模（AI 整理）", "AI 补充描述", "THREE_DIMENSIONAL_MODEL",
                        List.of("输送", "模块"), null, null));
        var view = service.confirm(suggestion.id(), ADMIN, "管理员", CONTENT_ADMIN_ROLE);
        assertThat(view.status()).isEqualTo(AiSuggestionStatus.CONFIRMED);
        assertThat(view.resolvedBy()).isEqualTo(ADMIN);
        assertThat(view.source()).isEqualTo("AI");
        var asset = assetRepository.findById(103).orElseThrow();
        assertThat(asset.name()).isEqualTo("输送模块布置数模（AI 整理）");
        assertThat(asset.description()).isEqualTo("AI 补充描述");
        assertThat(asset.assetType()).hasToString("THREE_DIMENSIONAL_MODEL");
        assertThat(asset.tags()).containsExactlyInAnyOrder("输送", "模块");
        assertThat(logs.count(new OperationLogCriteria("", "AI_SUGGESTION_CONFIRMED", "ASSET",
                null, null, 1, 20))).isEqualTo(1);
    }

    @Test
    void confirmOutOfScopeIsForbiddenForScopedUserEvenWithRole() {
        var suggestion = registerForAsset(105, new AiProposedFields("改名", "", null, List.of(), null, null));
        assertThatThrownBy(() -> service.confirm(suggestion.id(), CURATOR, "陈工", CONTENT_ADMIN_ROLE))
                .isInstanceOf(AiSuggestionScopeException.class);
        assertThat(pendingOf(suggestion.id()).status()).isEqualTo(AiSuggestionStatus.PENDING);
    }

    @Test
    void confirmRequiresCurationRoleForInScopeUser() {
        var suggestion = registerForAsset(103, new AiProposedFields("改名", "", null, List.of(), null, null));
        assertThatThrownBy(() -> service.confirm(suggestion.id(), CURATOR, "陈工", ""))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThat(pendingOf(suggestion.id()).status()).isEqualTo(AiSuggestionStatus.PENDING);
    }

    @Test
    void confirmTwiceFailsWithStateConflictAndAppliesOnlyOnce() {
        var suggestion = registerForAsset(103, new AiProposedFields("命名", "", null, List.of(), null, null));
        service.confirm(suggestion.id(), ADMIN, "管理员", CONTENT_ADMIN_ROLE);
        assertThatThrownBy(() -> service.confirm(suggestion.id(), ADMIN, "管理员", CONTENT_ADMIN_ROLE))
                .isInstanceOf(AiSuggestionStateException.class);
        assertThat(assetRepository.findById(103).orElseThrow().name()).isEqualTo("命名");
    }

    @Test
    void rejectMarksSuggestionRejectedAndLeavesAssetUntouched() {
        var suggestion = registerForAsset(103, new AiProposedFields("不应采纳的命名", "", null, List.of(), null, null));
        var view = service.reject(suggestion.id(), ADMIN, CONTENT_ADMIN_ROLE);
        assertThat(view.status()).isEqualTo(AiSuggestionStatus.REJECTED);
        assertThatThrownBy(() -> service.confirm(suggestion.id(), ADMIN, "管理员", CONTENT_ADMIN_ROLE))
                .isInstanceOf(AiSuggestionStateException.class);
        assertThat(assetRepository.findById(103).orElseThrow().name()).isEqualTo("输送模块布置数模");
    }

    @Test
    void confirmOnStandardizedAssetFailsAndRevertsSuggestionToPending() {
        var suggestion = registerForAsset(101, new AiProposedFields("改名标准化资产", "", null, List.of(), null, null));
        assertThatThrownBy(() -> service.confirm(suggestion.id(), ADMIN, "管理员", CONTENT_ADMIN_ROLE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅草稿或待整理");
        assertThat(pendingOf(suggestion.id()).status()).isEqualTo(AiSuggestionStatus.PENDING);
    }

    @Test
    void listFiltersByUserScope() {
        registerForAsset(103, new AiProposedFields("宁德建议", "", null, List.of(), null, null));
        registerForAsset(105, new AiProposedFields("溧阳建议", "", null, List.of(), null, null));
        var scoped = service.list(CURATOR, null, null, null, 1, 20);
        assertThat(scoped.total()).isEqualTo(1);
        assertThat(scoped.items().getFirst().targetId()).isEqualTo(103);
        assertThat(service.list(ADMIN, null, null, null, 1, 20).total()).isEqualTo(2);
    }

    @Test
    void listIsEmptyForUnknownUser() {
        registerForAsset(103, new AiProposedFields("宁德建议", "", null, List.of(), null, null));
        assertThat(service.list("demo-user", null, null, null, 1, 20).total()).isZero();
    }

    @Test
    void confirmUnknownSuggestionFails() {
        assertThatThrownBy(() -> service.confirm(9999L, ADMIN, "管理员", CONTENT_ADMIN_ROLE))
                .isInstanceOf(AiSuggestionNotFoundException.class);
    }
}
