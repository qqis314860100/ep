package com.tianshu.assets.governance.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionService;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionService.SaveResultDraftCommand;
import com.tianshu.assets.governance.execution.domain.GovernanceItem;
import com.tianshu.assets.governance.execution.domain.GovernanceResultStatus;
import com.tianshu.assets.governance.execution.domain.GovernanceResultVersion;
import com.tianshu.assets.governance.task.domain.GovernanceRuleSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/governance")
public class GovernanceExecutionController {

    private final GovernanceExecutionService service;
    private final ObjectMapper objectMapper;

    public GovernanceExecutionController(GovernanceExecutionService service) {
        this(service, new ObjectMapper());
    }

    @Autowired
    public GovernanceExecutionController(GovernanceExecutionService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/tasks/{taskId}/items")
    public List<ItemExecutionResponse> items(@PathVariable long taskId) {
        return service.items(taskId).stream().map(ItemExecutionResponse::from).toList();
    }

    @PutMapping("/items/{itemId}/result-draft")
    public GovernanceResultResponse saveDraft(
            @PathVariable long itemId,
            @Valid @RequestBody SaveResultDraftRequest request) {
        return resultResponse(service.saveDraft(itemId, request.toCommand()));
    }

    @PostMapping("/items/{itemId}/submit")
    public GovernanceResultResponse submit(
            @PathVariable long itemId,
            @Valid @RequestBody SubmitResultRequest request) {
        return resultResponse(service.submit(
                itemId, request.resultVersionId(), request.resultVersion(), request.actorUserId()));
    }

    private GovernanceResultResponse resultResponse(GovernanceResultVersion result) {
        try {
            return GovernanceResultResponse.from(result, objectMapper.readTree(result.proposedValueJson()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("已保存的治理结果 JSON 无法解析", exception);
        }
    }

    public record SaveResultDraftRequest(
            @Min(0) long itemVersion,
            @Min(0) long assetVersion,
            @NotNull JsonNode proposedValue,
            @NotBlank String actorUserId) {
        @AssertTrue(message = "建议值不能为空")
        public boolean isProposedValuePresent() {
            return proposedValue != null && !proposedValue.isNull();
        }

        SaveResultDraftCommand toCommand() {
            return new SaveResultDraftCommand(
                    itemVersion, assetVersion, proposedValue.toString(), actorUserId);
        }
    }

    public record SubmitResultRequest(
            @Min(1) long resultVersionId,
            @Min(0) long resultVersion,
            @NotBlank String actorUserId) {}

    public record GovernanceResultResponse(
            long id,
            long itemId,
            int governanceRound,
            int resultVersion,
            String field,
            String originalValueJson,
            JsonNode proposedValue,
            long standardVersion,
            Map<String, Long> dictionaryVersions,
            GovernanceResultStatus status,
            String actorUserId,
            Instant savedAt,
            Instant submittedAt,
            long version) {
        static GovernanceResultResponse from(GovernanceResultVersion result, JsonNode proposedValue) {
            return new GovernanceResultResponse(
                    result.id(), result.itemId(), result.governanceRound(), result.resultVersion(),
                    result.field().name(), result.originalValueJson(), proposedValue,
                    result.standardVersion(), result.dictionaryVersions(), result.status(),
                    result.actorUserId(), result.savedAt(), result.submittedAt(), result.version());
        }
    }

    public record ItemExecutionResponse(
            GovernanceItem item,
            GovernanceResultVersion currentResult,
            String originalFactJson,
            GovernanceRuleSnapshot ruleContext,
            String blockReason,
            Long reworkSourceItemId) {
        static ItemExecutionResponse from(GovernanceExecutionService.ItemExecutionContext context) {
            return new ItemExecutionResponse(
                    context.item(), context.currentResult(), context.originalFactJson(), context.ruleSnapshot(),
                    context.blockReason(), context.reworkSourceItemId());
        }
    }
}
