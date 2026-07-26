package com.tianshu.assets.governance.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionService;
import com.tianshu.assets.governance.application.GovernanceAuthorizationService;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionService.SaveResultDraftCommand;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionService.BatchExecutionResult;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionService.BatchResultCommand;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/governance")
public class GovernanceExecutionController {

    private final GovernanceExecutionService service;
    private final ObjectMapper objectMapper;
    private final GovernanceAuthorizationService authorizationService;

    public GovernanceExecutionController(GovernanceExecutionService service) {
        this(service, new ObjectMapper(), null);
    }

    public GovernanceExecutionController(GovernanceExecutionService service, ObjectMapper objectMapper) {
        this(service, objectMapper, null);
    }

    @Autowired
    public GovernanceExecutionController(
            GovernanceExecutionService service,
            ObjectMapper objectMapper,
            GovernanceAuthorizationService authorizationService) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/tasks/{taskId}/items")
    public List<ItemExecutionResponse> items(
            @PathVariable long taskId,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles) {
        authorizeTask(taskId, userId, roles);
        return service.items(taskId).stream().map(this::itemExecutionResponse).toList();
    }

    @PutMapping("/items/{itemId}/result-draft")
    public GovernanceResultResponse saveDraft(
            @PathVariable long itemId,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles,
            @Valid @RequestBody SaveResultDraftRequest request) {
        authorizeItem(itemId, userId, roles);
        return resultResponse(service.saveDraft(itemId, request.toCommand()));
    }

    @PostMapping("/items/{itemId}/submit")
    public GovernanceResultResponse submit(
            @PathVariable long itemId,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles,
            @Valid @RequestBody SubmitResultRequest request) {
        authorizeItem(itemId, userId, roles);
        return resultResponse(service.submit(
                itemId, request.resultVersionId(), request.resultVersion(), request.actorUserId()));
    }

    @PostMapping("/results/batch")
    public BatchExecutionResult batchResults(
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String userId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles,
            @Valid @RequestBody BatchResultsRequest request) {
        if (authorizationService != null) request.commands().stream().filter(java.util.Objects::nonNull)
                .forEach(command -> authorizationService.requireExecution(command.itemId(), userId, roles));
        return service.batchResults(
                request.idempotencyKey(),
                request.commands().stream()
                        .map(command -> command == null ? null : command.toCommand())
                        .toList());
    }

    private void authorizeItem(long itemId, String userId, String roles) {
        if (authorizationService != null) authorizationService.requireExecution(itemId, userId, roles);
    }

    private void authorizeTask(long taskId, String userId, String roles) {
        if (authorizationService != null) authorizationService.requireExecutionTask(taskId, userId, roles);
    }

    private GovernanceResultResponse resultResponse(GovernanceResultVersion result) {
        try {
            return GovernanceResultResponse.from(result, objectMapper.readTree(result.proposedValueJson()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("已保存的治理结果 JSON 无法解析", exception);
        }
    }

    private ItemExecutionResponse itemExecutionResponse(
            GovernanceExecutionService.ItemExecutionContext context) {
        return new ItemExecutionResponse(
                context.item(),
                context.currentResult() == null ? null : resultResponse(context.currentResult()),
                context.originalFactJson(), context.ruleSnapshot(),
                context.blockReason(), context.reworkSourceItemId());
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

    public record BatchResultsRequest(
            @NotBlank String idempotencyKey,
            @NotNull List<@Valid BatchResultRequest> commands) {
        @AssertTrue(message = "批量治理结果不能为空")
        public boolean hasCommands() {
            return commands != null && !commands.isEmpty();
        }
    }

    public record BatchResultRequest(
            @Min(1) long itemId,
            @Min(0) long itemVersion,
            @Min(0) long assetVersion,
            @NotNull GovernanceField targetField,
            @Min(1) long standardVersion,
            @NotBlank String scopeFingerprint,
            @NotNull JsonNode proposedValue,
            @NotBlank String actorUserId) {
        BatchResultCommand toCommand() {
            return new BatchResultCommand(
                    itemId, itemVersion, assetVersion, targetField, standardVersion,
                    scopeFingerprint, proposedValue.toString(), actorUserId);
        }
    }

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
            String reworkReason,
            String actorUserId,
            Instant savedAt,
            Instant submittedAt,
            long version) {
        static GovernanceResultResponse from(GovernanceResultVersion result, JsonNode proposedValue) {
            return new GovernanceResultResponse(
                    result.id(), result.itemId(), result.governanceRound(), result.resultVersion(),
                    result.field().name(), result.originalValueJson(), proposedValue,
                    result.standardVersion(), result.dictionaryVersions(), result.status(),
                    result.reworkReason(), result.actorUserId(), result.savedAt(),
                    result.submittedAt(), result.version());
        }
    }

    public record ItemExecutionResponse(
            GovernanceItem item,
            GovernanceResultResponse currentResult,
            String originalFactJson,
            GovernanceRuleSnapshot ruleContext,
            String blockReason,
            Long reworkSourceItemId) {}
}
