package com.tianshu.assets.ai.domain;

import java.time.Instant;
import java.util.List;

/** AI 建议聚合：待确认状态下由人工确认/驳回，确认后经资产写入链路生效。 */
public record AiSuggestion(
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
        Instant resolvedAt,
        long version) {

    public AiSuggestion {
        targetTitle = targetTitle == null ? "" : targetTitle.trim();
        status = status == null ? AiSuggestionStatus.PENDING : status;
        source = source == null || source.isBlank() ? "AI" : source.trim();
        proposed = proposed == null ? new AiProposedFields("", "", "", List.of(), "", "") : proposed;
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        createdBy = createdBy == null ? "" : createdBy.trim();
        resolvedBy = resolvedBy == null ? "" : resolvedBy.trim();
    }

    public AiSuggestion asResolved(AiSuggestionStatus nextStatus, String resolverUserId, Instant resolvedAt) {
        return new AiSuggestion(id, targetType, targetId, targetTitle, nextStatus, source, proposed, evidence,
                confidence, scopes, createdBy, createdAt, resolverUserId, resolvedAt, version);
    }

    public boolean isPending() {
        return status == AiSuggestionStatus.PENDING;
    }
}
