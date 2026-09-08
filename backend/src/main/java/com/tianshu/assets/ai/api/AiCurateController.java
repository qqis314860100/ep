package com.tianshu.assets.ai.api;

import com.tianshu.assets.ai.application.AiCurateService;
import com.tianshu.assets.ai.application.AiSuggestionService.AiSuggestionView;
import com.tianshu.assets.ai.domain.AiSuggestionTargetType;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI 编目重新整理入口（T4）：内容管理员/系统管理员、目标在范围内即可同步重新触发一次编目。 */
@RestController
@RequestMapping("/api/v1/ai/targets/{targetType}/{targetId}/suggestions")
public class AiCurateController {

    private final AiCurateService aiCurateService;

    public AiCurateController(AiCurateService aiCurateService) {
        this.aiCurateService = aiCurateService;
    }

    @PostMapping("/regenerate")
    public AiSuggestionView regenerate(
            @PathVariable AiSuggestionTargetType targetType,
            @PathVariable @Min(1) long targetId,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles) {
        return aiCurateService.regenerate(targetType, targetId, userId, roles);
    }
}
