package com.tianshu.assets.governance.api;

import com.tianshu.assets.governance.acceptance.application.GovernanceAcceptanceService;
import com.tianshu.assets.governance.application.GovernanceAuthorizationService;
import com.tianshu.assets.governance.acceptance.application.GovernanceQualityService;
import com.tianshu.assets.governance.acceptance.application.GovernanceAcceptancePreparationService;
import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceRound;
import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceSample;
import com.tianshu.assets.governance.task.application.GovernanceReworkService;
import com.tianshu.assets.governance.task.domain.GovernanceTask;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/api/v1/governance")
public class GovernanceAcceptanceController {

    private final GovernanceAcceptanceService acceptanceService;
    private final GovernanceQualityService qualityService;
    private final GovernanceReworkService reworkService;
    private final GovernanceAuthorizationService authorizationService;
    private final GovernanceAcceptancePreparationService preparationService;

    public GovernanceAcceptanceController(
            GovernanceAcceptanceService acceptanceService,
            GovernanceQualityService qualityService,
            GovernanceReworkService reworkService) {
        this(acceptanceService, qualityService, reworkService, null, null);
    }

    @Autowired
    public GovernanceAcceptanceController(
            GovernanceAcceptanceService acceptanceService,
            GovernanceQualityService qualityService,
            GovernanceReworkService reworkService,
            GovernanceAuthorizationService authorizationService,
            GovernanceAcceptancePreparationService preparationService) {
        this.acceptanceService = acceptanceService;
        this.qualityService = qualityService;
        this.reworkService = reworkService;
        this.authorizationService = authorizationService;
        this.preparationService = preparationService;
    }

    @GetMapping("/tasks/{taskId}/acceptance-rounds/current")
    public GovernanceAcceptanceRound current(
            @PathVariable long taskId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles) {
        authorizeAcceptance(roles);
        return preparationService == null
                ? acceptanceService.current(taskId)
                : preparationService.currentOrOpen(taskId);
    }

    @PutMapping("/acceptance-rounds/{roundId}/samples/{itemId}")
    public GovernanceAcceptanceSample saveSample(
            @PathVariable long roundId,
            @PathVariable long itemId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles,
            @Valid @RequestBody SampleRequest request) {
        authorizeAcceptance(roles);
        return qualityService.saveSample(
                roundId, itemId, request.passed(), request.issueDescription(),
                request.reviewerUserId(), request.sampleVersion());
    }

    @PostMapping("/tasks/{taskId}/acceptance-rounds/{roundId}/complete")
    public GovernanceAcceptanceService.CompletionResult complete(
            @PathVariable long taskId,
            @PathVariable long roundId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles,
            @Valid @RequestBody CompleteRequest request) {
        authorizeAcceptance(roles);
        return acceptanceService.complete(
                taskId, roundId, request.roundVersion(), request.operatorUserId());
    }

    @PostMapping("/tasks/{taskId}/rework")
    public GovernanceTask openRework(
            @PathVariable long taskId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles,
            @Valid @RequestBody ReworkRequest request) {
        authorizeAcceptance(roles);
        return reworkService.open(
                taskId, request.taskVersion(), request.reason(), request.actorUserId());
    }

    public record SampleRequest(
            @NotNull Boolean passed,
            String issueDescription,
            @NotBlank String reviewerUserId,
            @Min(0) long sampleVersion) {
        @AssertTrue(message = "验收不通过必须填写问题说明")
        public boolean isFailedDescriptionPresent() {
            return !Boolean.FALSE.equals(passed)
                    || issueDescription != null && !issueDescription.isBlank();
        }
    }

    public record CompleteRequest(
            @Min(0) long roundVersion,
            @NotBlank String operatorUserId) {}

    public record ReworkRequest(
            @Min(0) long taskVersion,
            @NotBlank String reason,
            @NotBlank String actorUserId) {}

    private void authorizeAcceptance(String roles) {
        if (authorizationService != null) authorizationService.requireAcceptance(roles);
    }
}
