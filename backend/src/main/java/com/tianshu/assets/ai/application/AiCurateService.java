package com.tianshu.assets.ai.application;

import com.tianshu.assets.ai.application.AiCapabilityClient.DocumentRequest;
import com.tianshu.assets.ai.application.AiCapabilityClient.ExtractionResult;
import com.tianshu.assets.ai.application.AiCapabilityClient.IngestResult;
import com.tianshu.assets.ai.domain.AiProposedFields;
import com.tianshu.assets.ai.domain.AiSuggestionTargetType;
import com.tianshu.assets.ai.domain.AiTargetScope;
import com.tianshu.assets.asset.application.AssetNotFoundException;
import com.tianshu.assets.asset.domain.Asset;
import com.tianshu.assets.asset.domain.AssetRepository;
import com.tianshu.assets.common.file.FileStorage;
import com.tianshu.assets.document.application.DocumentNotFoundException;
import com.tianshu.assets.document.domain.DocumentRepository;
import com.tianshu.assets.document.domain.KnowledgeDocument;
import com.tianshu.assets.system.domain.SystemUserRepository;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * AI 编目建议触发服务（T4）：加载目标（资产/知识文档）→ 取首个可解析附件字节 →
 * 能力服务文档入库 + 元数据抽取 → 注册为待确认建议（同目标旧待确认自动作废）。
 * 抽取失败重试一次后抛出（由调度方记录）。附件字节走 fileContentBase64 + fileName 运输。
 */
@Service
public class AiCurateService {

    private static final int ATTEMPTS = 2;
    private static final Set<String> CURATION_ROLES = Set.of("CONTENT_ADMIN", "SYSTEM_ADMIN");
    private static final String CURATION_SOURCE = "ai-curation";
    private static final Set<String> PARSEABLE_EXTENSIONS =
            Set.of(".pdf", ".docx", ".md", ".markdown", ".txt", ".html", ".htm", ".csv");

    private final AiCapabilityClient capability;
    private final AiSuggestionService suggestions;
    private final AssetRepository assets;
    private final DocumentRepository documents;
    private final SystemUserRepository users;
    private final FileStorage fileStorage;
    private final String namespace;

    @Autowired
    public AiCurateService(AiCapabilityClient capability, AiSuggestionService suggestions,
            AssetRepository assets, DocumentRepository documents, SystemUserRepository users,
            FileStorage fileStorage, @Value("${ai.capability.namespace:ep-docs}") String namespace) {
        this.capability = capability;
        this.suggestions = suggestions;
        this.assets = assets;
        this.documents = documents;
        this.users = users;
        this.fileStorage = fileStorage;
        this.namespace = namespace;
    }

    public AiCurateService(AiCapabilityClient capability, AiSuggestionService suggestions,
            AssetRepository assets, DocumentRepository documents, SystemUserRepository users,
            String namespace) {
        this(capability, suggestions, assets, documents, users, null, namespace);
    }

    /** 对资产触发一次编目（返回新登记的待确认建议）。 */
    public AiSuggestionService.AiSuggestionView curateAsset(long assetId) {
        var asset = assets.findById(assetId).orElseThrow(() -> new AssetNotFoundException(assetId));
        var scopes = asset.scopes().stream().map(AiTargetScope::fromAssetScope).toList();
        var transport = assetTransport(asset);
        return curate(AiSuggestionTargetType.ASSET, assetId, asset.name(), scopes, transport, extractionResult -> {
            var proposed = new AiProposedFields(
                    extractionResult.name(),
                    extractionResult.description(),
                    extractionResult.assetTypeCode(),
                    extractionResult.tags(),
                    extractionResult.summary(),
                    "");
            return new AiSuggestionService.AiSuggestionDraft(
                    AiSuggestionTargetType.ASSET, assetId, asset.name(), proposed,
                    extractionResult.evidence(), extractionResult.confidence(), scopes, CURATION_SOURCE);
        });
    }

    /** 对知识文档触发一次编目（返回新登记的待确认建议）。 */
    public AiSuggestionService.AiSuggestionView curateDocument(long documentId) {
        var document = documents.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("文档不存在"));
        var scopes = document.scopes().stream().map(AiTargetScope::fromDocumentScope).toList();
        var transport = documentTransport(document);
        return curate(AiSuggestionTargetType.KNOWLEDGE_DOC, documentId, document.title(), scopes, transport,
                extractionResult -> {
                    var proposed = new AiProposedFields(
                            extractionResult.name(),
                            "",
                            "",
                            List.of(),
                            extractionResult.summary(),
                            extractionResult.categoryCode());
                    return new AiSuggestionService.AiSuggestionDraft(
                            AiSuggestionTargetType.KNOWLEDGE_DOC, documentId, document.title(), proposed,
                            extractionResult.evidence(), extractionResult.confidence(), scopes, CURATION_SOURCE);
                });
    }

    /** 「重新整理」入口：内容管理员/系统管理员且目标在本人范围内才可触发（同步执行，返回新建议）。 */
    public AiSuggestionService.AiSuggestionView regenerate(AiSuggestionTargetType targetType, long targetId,
            String userId, String roles) {
        if (targetType == null || userId == null || userId.isBlank()) {
            throw new AiSuggestionValidationException("目标与操作人不能为空");
        }
        if (roles == null || java.util.Arrays.stream(roles.split(","))
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .noneMatch(CURATION_ROLES::contains)) {
            throw new com.tianshu.assets.asset.application.ForbiddenOperationException(
                    "仅内容管理员或系统管理员可以触发重新整理");
        }
        var user = users.findByUserId(userId.trim()).orElse(null);
        if (user == null) {
            throw new AiSuggestionScopeException("用户未识别，无法触发重新整理");
        }
        var snapshot = loadSnapshot(targetType, targetId);
        // 与建议可见性一致：范围为空=不受限；有范围则目标至少一个范围与其一致
        var inScope = user.scopes().isEmpty()
                || (!snapshot.scopes().isEmpty() && snapshot.scopes().stream().anyMatch(targetScope ->
                        user.scopes().stream().anyMatch(userScope ->
                                (userScope.base().isBlank() || userScope.base().equals(targetScope.base()))
                                        && (userScope.productLine().isBlank()
                                                || userScope.productLine().equals(targetScope.productLine())))));
        if (!inScope) {
            throw new AiSuggestionScopeException("目标不在你的范围内，无法触发重新整理");
        }
        return targetType == AiSuggestionTargetType.ASSET
                ? curateAsset(targetId)
                : curateDocument(targetId);
    }

    private AiSuggestionService.AiSuggestionView curate(AiSuggestionTargetType targetType, long targetId,
            String title, List<AiTargetScope> scopes, Transport transport,
            java.util.function.Function<ExtractionResult, AiSuggestionService.AiSuggestionDraft> mapper) {
        var request = new DocumentRequest(namespace, targetType.name(), targetId, title, scopes,
                transport.base64(), transport.fileName());
        AiCapabilityException lastFailure = null;
        // 重试以能力服务失败为准（能力契约要求 ingest 幂等；仍失败则由触发方记录并支持手动重新整理）
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                IngestResult result = capability.ingestDocument(request);
                if (result == null || !result.ok()) {
                    throw new AiCapabilityException(AiCapabilityException.AiCapabilityError.UNAVAILABLE,
                            "文档入库失败: " + (result == null ? "无响应" : result.message()));
                }
                var extraction = capability.extractMetadata(request);
                return suggestions.register(mapper.apply(extraction));
            } catch (AiCapabilityException exception) {
                lastFailure = exception;
            }
        }
        throw lastFailure != null
                ? lastFailure
                : new AiCapabilityException(AiCapabilityException.AiCapabilityError.UNAVAILABLE,
                        "AI 编目抽取失败");
    }

    private Transport assetTransport(Asset asset) {
        if (fileStorage == null) {
            return Transport.none();
        }
        for (var file : asset.files()) {
            if (file.storageKey().isBlank() || !parseable(file.name())) {
                continue;
            }
            var opened = fileStorage.open(file.storageKey());
            if (opened.isPresent() && opened.get().content().length > 0) {
                return new Transport(file.name(), Base64.getEncoder().encodeToString(opened.get().content()));
            }
        }
        return Transport.none();
    }

    private Transport documentTransport(KnowledgeDocument document) {
        if (fileStorage == null || document.currentVersion() == null) {
            return Transport.none();
        }
        for (var file : document.currentVersion().files()) {
            if (file.storageKey() == null || file.storageKey().isBlank() || !parseable(file.name())) {
                continue;
            }
            var opened = fileStorage.open(file.storageKey());
            if (opened.isPresent() && opened.get().content().length > 0) {
                return new Transport(file.name(), Base64.getEncoder().encodeToString(opened.get().content()));
            }
        }
        return Transport.none();
    }

    private static boolean parseable(String fileName) {
        var dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return PARSEABLE_EXTENSIONS.contains(fileName.substring(dot).toLowerCase(Locale.ROOT));
    }

    private TargetSnapshot loadSnapshot(AiSuggestionTargetType targetType, long targetId) {
        if (targetType == AiSuggestionTargetType.ASSET) {
            var asset = assets.findById(targetId).orElseThrow(() -> new AssetNotFoundException(targetId));
            return new TargetSnapshot(asset.name(),
                    asset.scopes().stream().map(AiTargetScope::fromAssetScope).toList());
        }
        var document = documents.findById(targetId)
                .orElseThrow(() -> new DocumentNotFoundException("文档不存在"));
        return new TargetSnapshot(document.title(),
                document.scopes().stream().map(AiTargetScope::fromDocumentScope).toList());
    }

    private record TargetSnapshot(String title, List<AiTargetScope> scopes) {
    }

    private record Transport(String fileName, String base64) {

        static Transport none() {
            return new Transport("", "");
        }
    }
}
