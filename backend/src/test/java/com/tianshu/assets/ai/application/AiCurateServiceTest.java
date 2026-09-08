package com.tianshu.assets.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tianshu.assets.ai.domain.AiSuggestionStatus;
import com.tianshu.assets.ai.domain.AiSuggestionTargetType;
import com.tianshu.assets.ai.infrastructure.AiCurateListener;
import com.tianshu.assets.ai.infrastructure.FakeAiCapabilityClient;
import com.tianshu.assets.ai.infrastructure.InMemoryAiSuggestionRepository;
import com.tianshu.assets.asset.application.AssetWriteService;
import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import com.tianshu.assets.document.domain.DocumentFile;
import com.tianshu.assets.document.domain.DocumentScope;
import com.tianshu.assets.document.domain.DocumentScopeMode;
import com.tianshu.assets.document.domain.DocumentStatus;
import com.tianshu.assets.document.domain.DocumentVersion;
import com.tianshu.assets.document.domain.DocumentVersionStatus;
import com.tianshu.assets.document.domain.KnowledgeDocument;
import com.tianshu.assets.document.infrastructure.InMemoryDocumentRepository;
import com.tianshu.assets.governance.infrastructure.SpringGovernanceJobDispatcher;
import com.tianshu.assets.system.infrastructure.InMemoryOperationLogStore;
import com.tianshu.assets.system.infrastructure.InMemorySystemUserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiCurateServiceTest {

    private static final String ADMIN = "emp-admin";
    private static final String SCOPED = "emp-chen";

    private InMemoryAssetRepository assets;
    private InMemoryDocumentRepository documents;
    private InMemoryAiSuggestionRepository suggestions;
    private FakeAiCapabilityClient capability;
    private AiCurateService curate;
    private AiCurateListener listener;

    @BeforeEach
    void setUp() {
        assets = new InMemoryAssetRepository();
        documents = new InMemoryDocumentRepository();
        suggestions = new InMemoryAiSuggestionRepository();
        capability = new FakeAiCapabilityClient();
        var users = new InMemorySystemUserRepository();
        var logs = new InMemoryOperationLogStore();
        var suggestionService = new AiSuggestionService(suggestions, users, new AssetWriteService(assets), logs);
        curate = new AiCurateService(capability, suggestionService, assets, documents, users, "ep-docs");
        var dispatcher = new SpringGovernanceJobDispatcher(task -> task.run(), null);
        listener = new AiCurateListener(dispatcher, curate);
    }

    private long seedPublishedDocument() {
        var now = Instant.parse("2026-09-09T10:00:00Z");
        var version = new DocumentVersion(0, 0, "V1", "初始版本", DocumentVersionStatus.PUBLISHED,
                List.of(new DocumentFile(0, "ops.docx", "DOCX", 1024, true, "key", "sha")),
                "emp-wang", now, "emp-wang", now);
        var document = new KnowledgeDocument(0, "DOC-001", "焊接工装操作说明", "焊接工装操作规程", "SOP",
                "emp-wang", "王工", "资料管理组", DocumentScopeMode.UNCLASSIFIED,
                List.of(new DocumentScope(0, 0, "乘用车", "底部水冷", "H03", "宁德基地", "A 拉线", "焊接段")),
                DocumentStatus.PUBLISHED, version.id(), version, now, now, 1);
        return documents.save(document).id();
    }

    @Test
    void curateAssetRegistersPendingSuggestionWithFieldsAndScopes() {
        var view = curate.curateAsset(103);

        assertThat(view.status()).isEqualTo(AiSuggestionStatus.PENDING);
        assertThat(view.targetType()).isEqualTo(AiSuggestionTargetType.ASSET);
        assertThat(view.targetId()).isEqualTo(103);
        assertThat(view.proposed().name()).isEqualTo("资产名称建议");
        assertThat(view.proposed().assetTypeCode()).isEqualTo("THREE_DIMENSIONAL_MODEL");
        assertThat(view.proposed().tags()).contains("标签A");
        assertThat(view.evidence()).contains("证据片段");
        assertThat(view.confidence()).isEqualTo(0.9);
        assertThat(view.scopes()).anySatisfy(scope -> assertThat(scope.base()).isEqualTo("宁德基地"));
        assertThat(suggestions.findAll()).hasSize(1);
    }

    @Test
    void curateDocumentRegistersPendingSuggestion() {
        var documentId = seedPublishedDocument();
        var view = curate.curateDocument(documentId);

        assertThat(view.targetType()).isEqualTo(AiSuggestionTargetType.KNOWLEDGE_DOC);
        assertThat(view.targetId()).isEqualTo(documentId);
        assertThat(view.proposed().summary()).isEqualTo("摘要建议");
        assertThat(view.proposed().categoryCode()).isEmpty();
    }

    @Test
    void repeatedCurateSupersedesPriorPendingSuggestion() {
        var first = curate.curateAsset(103);
        var second = curate.curateAsset(103);
        assertThat(second.id()).isNotEqualTo(first.id());

        var stored = suggestions.findAll().stream().toList();
        assertThat(stored).hasSize(2);
        assertThat(stored.stream().filter(suggestion -> suggestion.id() == first.id())
                .findFirst().orElseThrow().status()).isEqualTo(AiSuggestionStatus.SUPERSEDED);
        assertThat(stored.stream().filter(suggestion -> suggestion.id() == second.id())
                .findFirst().orElseThrow().status()).isEqualTo(AiSuggestionStatus.PENDING);
    }

    @Test
    void capabilityFailureFailsAfterRetriesAndLeavesNoSuggestion() {
        capability.failWith(new AiCapabilityException(
                AiCapabilityException.AiCapabilityError.UNAVAILABLE, "能力服务不可用"));
        assertThatThrownBy(() -> curate.curateAsset(103)).isInstanceOf(AiCapabilityException.class);
        assertThat(suggestions.findAll()).isEmpty();
        assertThat(capability.lastRequests()).hasSize(2);
    }

    @Test
    void listenerFiresCurationForSavedAssetAndPublishedDocument() {
        var documentId = seedPublishedDocument();
        listener.onAssetSaved(103);
        listener.onDocumentPublished(documentId);
        var stored = suggestions.findAll().stream().toList();
        assertThat(stored).hasSize(2);
        assertThat(stored).extracting(suggestion -> suggestion.targetType().name())
                .containsExactlyInAnyOrder("ASSET", "KNOWLEDGE_DOC");
    }

    @Test
    void regenerateRequiresRoleAndScope() {
        var view = curate.regenerate(AiSuggestionTargetType.ASSET, 103, ADMIN, "CONTENT_ADMIN");
        assertThat(view.status()).isEqualTo(AiSuggestionStatus.PENDING);

        assertThatThrownBy(() -> curate.regenerate(AiSuggestionTargetType.ASSET, 103, ADMIN, ""))
                .isInstanceOf(com.tianshu.assets.asset.application.ForbiddenOperationException.class);
        assertThatThrownBy(() -> curate.regenerate(AiSuggestionTargetType.ASSET, 105, SCOPED, "CONTENT_ADMIN"))
                .isInstanceOf(AiSuggestionScopeException.class);
    }
}
