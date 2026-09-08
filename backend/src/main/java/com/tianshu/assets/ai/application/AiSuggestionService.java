package com.tianshu.assets.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.ai.domain.AiProposedFields;
import com.tianshu.assets.ai.domain.AiSuggestion;
import com.tianshu.assets.ai.domain.AiSuggestionRepository;
import com.tianshu.assets.ai.domain.AiSuggestionStatus;
import com.tianshu.assets.ai.domain.AiSuggestionTargetType;
import com.tianshu.assets.ai.domain.AiTargetScope;
import com.tianshu.assets.asset.application.AssetWriteService;
import com.tianshu.assets.asset.application.ForbiddenOperationException;
import com.tianshu.assets.asset.domain.AssetType;
import com.tianshu.assets.system.domain.OperationLog;
import com.tianshu.assets.system.domain.OperationLogStore;
import com.tianshu.assets.system.domain.SystemUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 建议-确认域应用服务（AI 一期 T1）。
 *
 * <p>信任边界：本服务是 AI 对业务域的唯一写入口——只产出/确认「建议」，生效走现有资产写入链路。
 * 可见与操作按当前用户 AssetScope 过滤（范围外不可见、不可确认/驳回），确认/驳回要求内容管理员或系统管理员角色；
 * 全部决策写操作日志（含依据片段快照）。</p>
 *
 * <p>范围说明：登记当前仅支持资产目标；知识文档目标与范围提示（scopeHints）的应用随后续「入库触发 AI 编目」切片落地。</p>
 */
@Service
public class AiSuggestionService {

    private static final Set<String> CURATION_ROLES = Set.of("CONTENT_ADMIN", "SYSTEM_ADMIN");
    private static final String AI_SOURCE = "AI";

    private final AiSuggestionRepository suggestions;
    private final SystemUserRepository users;
    private final AssetWriteService assetWrite;
    private final OperationLogStore operationLogs;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public AiSuggestionService(AiSuggestionRepository suggestions, SystemUserRepository users,
            AssetWriteService assetWrite, OperationLogStore operationLogs) {
        this(suggestions, users, assetWrite, operationLogs, Clock.systemUTC());
    }

    public AiSuggestionService(AiSuggestionRepository suggestions, SystemUserRepository users,
            AssetWriteService assetWrite, OperationLogStore operationLogs, Clock clock) {
        this.suggestions = suggestions;
        this.users = users;
        this.assetWrite = assetWrite;
        this.operationLogs = operationLogs;
        this.clock = clock;
    }

    /** 登记一条 AI 建议（抽取完成后调用；同目标旧待确认建议作废为 SUPERSEDED）。 */
    @Transactional
    public AiSuggestionView register(AiSuggestionDraft draft) {
        if (draft == null) {
            throw new AiSuggestionValidationException("AI 建议内容不能为空");
        }
        if (draft.targetType() == null || draft.targetType() != AiSuggestionTargetType.ASSET) {
            throw new AiSuggestionValidationException("AI 建议当前仅支持资产目标，文档目标建议随入库触发切片落地");
        }
        if (draft.targetId() <= 0) {
            throw new AiSuggestionValidationException("AI 建议目标不能为空");
        }
        if (draft.proposed() == null) {
            throw new AiSuggestionValidationException("AI 建议字段不能为空");
        }
        if (draft.createdBy() == null || draft.createdBy().isBlank()) {
            throw new AiSuggestionValidationException("AI 建议来源不能为空");
        }
        var now = Instant.now(clock);
        suggestions.findByTarget(draft.targetType(), draft.targetId()).stream()
                .filter(AiSuggestion::isPending)
                .forEach(pending -> supersede(pending, now));
        var saved = suggestions.save(new AiSuggestion(
                0,
                draft.targetType(),
                draft.targetId(),
                draft.targetTitle(),
                AiSuggestionStatus.PENDING,
                AI_SOURCE,
                draft.proposed(),
                draft.evidence(),
                draft.confidence(),
                draft.scopes(),
                draft.createdBy(),
                now,
                "",
                null,
                0));
        return AiSuggestionView.from(saved);
    }

    /** 清单：按当前用户 AssetScope 过滤 + 目标/状态筛选 + 分页；未知名用户不可见（fail-closed）。 */
    public SuggestionPage list(String userId, AiSuggestionTargetType targetType, Long targetId,
            AiSuggestionStatus status, int page, int perPage) {
        var matched = suggestions.findAll().stream()
                .filter(suggestion -> targetType == null || suggestion.targetType() == targetType)
                .filter(suggestion -> targetId == null || suggestion.targetId() == targetId)
                .filter(suggestion -> status == null || suggestion.status() == status)
                .filter(suggestion -> visibleTo(userId, suggestion))
                .sorted(Comparator.comparing(AiSuggestion::id).reversed())
                .toList();
        var total = matched.size();
        var from = Math.max(0, Math.min((page - 1) * perPage, total));
        var to = Math.min(from + perPage, total);
        var items = matched.subList(from, to).stream().map(AiSuggestionView::from).toList();
        return new SuggestionPage(items, total);
    }

    /** 确认建议（须内容管理员/系统管理员，目标在当前用户范围内，仅待确认）：先乐观锁定，再经资产写入链路生效。 */
    @Transactional
    public AiSuggestionView confirm(long suggestionId, String userId, String userName, String roles) {
        requireOperator(userId);
        var suggestion = requirePending(suggestionId);
        requireInScope(userId, suggestion);
        requireCurationRole(roles);
        var resolved = resolve(suggestion, AiSuggestionStatus.CONFIRMED, userId);
        try {
            applyTargetChanges(suggestion, userId, userName);
        } catch (RuntimeException exception) {
            revertToPending(resolved);
            throw exception;
        }
        appendAudit("AI_SUGGESTION_CONFIRMED", resolved, userId);
        return AiSuggestionView.from(resolved);
    }

    /** 驳回建议（须内容管理员/系统管理员，目标在当前用户范围内）：不产生业务变更。 */
    @Transactional
    public AiSuggestionView reject(long suggestionId, String userId, String roles) {
        requireOperator(userId);
        var suggestion = requirePending(suggestionId);
        requireInScope(userId, suggestion);
        requireCurationRole(roles);
        var resolved = resolve(suggestion, AiSuggestionStatus.REJECTED, userId);
        appendAudit("AI_SUGGESTION_REJECTED", resolved, userId);
        return AiSuggestionView.from(resolved);
    }

    private void requireCurationRole(String roles) {
        if (roles == null || java.util.Arrays.stream(roles.split(","))
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .noneMatch(CURATION_ROLES::contains)) {
            throw new ForbiddenOperationException("仅内容管理员或系统管理员可以确认或驳回 AI 建议");
        }
    }

    private void applyTargetChanges(AiSuggestion suggestion, String userId, String userName) {
        if (suggestion.targetType() == AiSuggestionTargetType.ASSET) {
            applyToAsset(suggestion, userId, userName);
            return;
        }
        throw new AiSuggestionStateException("文档目标建议的应用尚未开放");
    }

    private void applyToAsset(AiSuggestion suggestion, String userId, String userName) {
        var proposed = suggestion.proposed();
        if (!proposed.hasAssetChanges()) {
            return;
        }
        assetWrite.applyAiCuratedMetadata(
                suggestion.targetId(),
                nullIfBlank(proposed.name()),
                nullIfBlank(proposed.description()),
                parseAssetType(proposed.assetTypeCode()),
                proposed.tags().isEmpty() ? null : proposed.tags(),
                userId,
                userName);
    }

    private static AssetType parseAssetType(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return AssetType.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null; // 未知分类编码：不覆盖现有类型
        }
    }

    private static String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AiSuggestion requirePending(long suggestionId) {
        var suggestion = suggestions.findById(suggestionId)
                .orElseThrow(() -> new AiSuggestionNotFoundException("AI 建议不存在"));
        if (!suggestion.isPending()) {
            throw new AiSuggestionStateException("仅待确认的 AI 建议可以确认或驳回");
        }
        return suggestion;
    }

    private AiSuggestion resolve(AiSuggestion suggestion, AiSuggestionStatus status, String userId) {
        var now = Instant.now(clock);
        try {
            return suggestions.update(suggestion.asResolved(status, userId, now), suggestion.version());
        } catch (IllegalStateException exception) {
            throw new AiSuggestionStateException("AI 建议已被其他操作更新，请刷新后重试");
        }
    }

    private void revertToPending(AiSuggestion resolved) {
        try {
            suggestions.update(resolved.asResolved(AiSuggestionStatus.PENDING, "", null), resolved.version());
        } catch (IllegalStateException ignored) {
            // 回退失败保持已确认状态；应用失败已抛出，交由上层处理
        }
    }

    private void supersede(AiSuggestion pending, Instant now) {
        try {
            suggestions.update(pending.asResolved(AiSuggestionStatus.SUPERSEDED, "", now), pending.version());
        } catch (IllegalStateException ignored) {
            // 并发登记：该条已被其他操作处理，跳过
        }
    }

    private void appendAudit(String action, AiSuggestion suggestion, String userId) {
        try {
            operationLogs.append(new OperationLog(0, userId, action, suggestion.targetType().name(),
                    suggestion.targetId(),
                    objectMapper.writeValueAsString(Map.of(
                            "suggestionId", suggestion.id(),
                            "source", suggestion.source(),
                            "evidence", suggestion.evidence(),
                            "confidence", suggestion.confidence() == null ? "" : suggestion.confidence().toString())),
                    Instant.now(clock)));
        } catch (Exception exception) {
            throw new IllegalStateException("AI 建议审计写入失败", exception);
        }
    }

    private void requireOperator(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new AiSuggestionValidationException("操作人不能为空");
        }
    }

    private void requireInScope(String userId, AiSuggestion suggestion) {
        if (!visibleTo(userId, suggestion)) {
            throw new AiSuggestionScopeException("AI 建议不在你的范围内，无法查看或操作");
        }
    }

    /**
     * 可见性规则（fail-closed）：未知名用户不可见；用户范围为空视为不受限（内容管理员等）；
     * 否则建议至少有一个范围与用户的任一 {base, productLine} 范围一致。
     */
    private boolean visibleTo(String userId, AiSuggestion suggestion) {
        var user = users.findByUserId(userId).orElse(null);
        if (user == null) {
            return false;
        }
        if (user.scopes().isEmpty()) {
            return true;
        }
        if (suggestion.scopes().isEmpty()) {
            return false;
        }
        return suggestion.scopes().stream().anyMatch(targetScope -> user.scopes().stream().anyMatch(userScope ->
                (userScope.base().isBlank() || userScope.base().equals(targetScope.base()))
                        && (userScope.productLine().isBlank()
                                || userScope.productLine().equals(targetScope.productLine()))));
    }

    public record AiSuggestionDraft(
            AiSuggestionTargetType targetType,
            long targetId,
            String targetTitle,
            AiProposedFields proposed,
            List<String> evidence,
            Double confidence,
            List<AiTargetScope> scopes,
            String createdBy) {
    }

    public record SuggestionPage(List<AiSuggestionView> items, long total) {

        public SuggestionPage {
            items = List.copyOf(items);
        }
    }

    public record AiSuggestionView(
            long id,
            AiSuggestionTargetType targetType,
            long targetId,
            String targetTitle,
            AiSuggestionStatus status,
            String source,
            AiProposedFields proposed,
            List<String> evidence,
            Double confidence,
            List<AiTargetScope> scopes,
            String createdBy,
            Instant createdAt,
            String resolvedBy,
            Instant resolvedAt) {

        public static AiSuggestionView from(AiSuggestion suggestion) {
            return new AiSuggestionView(
                    suggestion.id(),
                    suggestion.targetType(),
                    suggestion.targetId(),
                    suggestion.targetTitle(),
                    suggestion.status(),
                    suggestion.source(),
                    suggestion.proposed(),
                    suggestion.evidence(),
                    suggestion.confidence(),
                    suggestion.scopes(),
                    suggestion.createdBy(),
                    suggestion.createdAt(),
                    suggestion.resolvedBy(),
                    suggestion.resolvedAt());
        }
    }
}
