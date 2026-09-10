package com.tianshu.assets.ai.api;

import com.tianshu.assets.ai.application.AiSuggestionService;
import com.tianshu.assets.ai.application.AiSuggestionService.AiSuggestionView;
import com.tianshu.assets.ai.domain.AiSuggestionStatus;
import com.tianshu.assets.ai.domain.AiSuggestionTargetType;
import com.tianshu.assets.asset.api.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** AI 建议-确认 API（AI 一期 T1）：清单按当前用户 AssetScope 过滤，确认/驳回均要求目标在范围内且具治理角色。 */
@RestController
@RequestMapping("/api/v1/ai/suggestions")
public class AiSuggestionController {

    private final AiSuggestionService aiSuggestionService;

    public AiSuggestionController(AiSuggestionService aiSuggestionService) {
        this.aiSuggestionService = aiSuggestionService;
    }

    @GetMapping
    public PageResponse<AiSuggestionView> list(
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId,
            @RequestParam(name = "target_type", required = false) AiSuggestionTargetType targetType,
            @RequestParam(name = "target_id", required = false) Long targetId,
            @RequestParam(required = false) AiSuggestionStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "per_page", defaultValue = "20") @Min(1) @Max(100) int perPage) {
        var result = aiSuggestionService.list(userId, targetType, targetId, status, page, perPage);
        return new PageResponse<>(result.items(), PageResponse.Meta.of(result.total(), page, perPage));
    }

    @PostMapping("/{id}/confirm")
    public AiSuggestionView confirm(
            @PathVariable @Min(1) long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId,
            @RequestHeader(name = "X-User-Name", defaultValue = "") String userName,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles) {
        return aiSuggestionService.confirm(id, userId, userName, roles);
    }

    @PostMapping("/{id}/reject")
    public AiSuggestionView reject(
            @PathVariable @Min(1) long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles) {
        return aiSuggestionService.reject(id, userId, roles);
    }
}
