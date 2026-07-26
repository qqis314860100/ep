package com.tianshu.assets.governance.api;

import com.tianshu.assets.governance.confirmation.application.GovernanceConfirmationService;
import com.tianshu.assets.governance.confirmation.application.GovernanceConfirmationService.CompletionResult;
import com.tianshu.assets.governance.confirmation.application.GovernanceConfirmationService.ConfirmationView;
import com.tianshu.assets.governance.confirmation.application.GovernanceConfirmationService.DecisionCommand;
import com.tianshu.assets.governance.confirmation.domain.GovernanceConfirmationDecision.Decision;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/governance")
public class GovernanceConfirmationController {

    private final GovernanceConfirmationService service;

    public GovernanceConfirmationController(GovernanceConfirmationService service) {
        this.service = service;
    }

    @GetMapping("/tasks/{taskId}/confirmation-rounds/current")
    public ConfirmationView current(@PathVariable long taskId) {
        return service.current(taskId);
    }

    @PutMapping("/confirmation-rounds/{roundId}/items/{itemId}/decision")
    public ConfirmationView decide(
            @PathVariable long roundId,
            @PathVariable long itemId,
            @Valid @RequestBody DecisionRequest request) {
        return service.decide(roundId, itemId, request.toCommand());
    }

    @PostMapping("/tasks/{taskId}/confirmation-rounds/{roundId}/complete")
    public CompletionResult complete(
            @PathVariable long taskId,
            @PathVariable long roundId,
            @Valid @RequestBody CompleteRequest request) {
        return service.complete(taskId, roundId, request.roundVersion());
    }

    public record DecisionRequest(
            @NotNull Decision decision,
            String comment,
            @Min(0) long decisionVersion,
            @NotBlank String confirmerUserId) {
        @AssertTrue(message = "退回必须填写确认意见")
        public boolean isRejectedCommentPresent() {
            return decision != Decision.REJECTED || comment != null && !comment.isBlank();
        }

        DecisionCommand toCommand() {
            return new DecisionCommand(decision, comment, decisionVersion, confirmerUserId);
        }
    }

    public record CompleteRequest(@Min(0) long roundVersion) {}
}
