package com.tianshu.assets.governance.api;

import com.tianshu.assets.governance.domain.GovernanceEmployee;
import com.tianshu.assets.governance.issue.application.GovernanceIssueService;
import com.tianshu.assets.governance.issue.application.GovernanceIssueService.CreateGovernanceTaskCommand;
import com.tianshu.assets.governance.task.application.GovernanceTaskApplicationService;
import com.tianshu.assets.governance.task.application.GovernanceTaskApplicationService.CreatePlanCommand;
import com.tianshu.assets.governance.task.application.GovernanceTaskApplicationService.PlanProjection;
import com.tianshu.assets.governance.task.application.GovernanceTaskApplicationService.TaskFilter;
import com.tianshu.assets.governance.task.application.GovernanceTaskStartService;
import com.tianshu.assets.governance.application.GovernanceValidationException;
import com.tianshu.assets.governance.task.domain.GovernancePlan;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/governance/tasks")
public class GovernanceTaskController {

    private final GovernanceTaskApplicationService service;
    private final GovernanceIssueService issueService;
    private final GovernanceTaskStartService startService;

    @Autowired
    public GovernanceTaskController(
            GovernanceTaskApplicationService service,
            GovernanceIssueService issueService,
            GovernanceTaskStartService startService) {
        this.service = service;
        this.issueService = issueService;
        this.startService = startService;
    }

    public GovernanceTaskController(
            GovernanceTaskApplicationService service, GovernanceIssueService issueService) {
        this(service, issueService, null);
    }

    @GetMapping
    public List<GovernanceTaskResponse> list(
            @RequestParam(required = false) GovernanceTaskStatus status,
            @RequestParam(required = false) String ownerUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueBefore,
            @RequestParam(required = false) GovernanceField field,
            @RequestParam(required = false) String scopeFingerprint) {
        return service.list(new TaskFilter(status, ownerUserId, dueBefore, field, scopeFingerprint))
                .stream().map(GovernanceTaskResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GovernanceTaskResponse create(@Valid @RequestBody CreateTaskRequest request) {
        return GovernanceTaskResponse.from(issueService.createTask(new CreateGovernanceTaskCommand(
                request.name(), request.issueIds(), request.ownerUserId(), request.ownerName(), request.dueDate())));
    }

    @GetMapping("/employees")
    public List<GovernanceEmployee> employees() {
        return service.employees();
    }

    @GetMapping("/{taskId}/plans")
    public List<PlanProjection> plans(@PathVariable long taskId) {
        return service.plans(taskId);
    }

    @GetMapping("/{taskId}")
    public GovernanceTaskResponse get(@PathVariable long taskId) {
        return GovernanceTaskResponse.from(service.detail(taskId));
    }

    @PatchMapping("/{taskId}/plans/{planId}")
    public GovernancePlan updatePlan(
            @PathVariable long taskId,
            @PathVariable long planId,
            @Valid @RequestBody UpdatePlanRequest request) {
        return service.rejectPlanMutation(taskId);
    }

    @PostMapping("/{taskId}/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public GovernancePlan createPlan(
            @PathVariable long taskId,
            @Valid @RequestBody CreatePlanRequest request) {
        service.requireClosedLoop(taskId);
        if (request.plannedQuantity() != null || request.completedQuantity() != null
                || request.quantityUnit() != null) {
            throw new GovernanceValidationException("闭环治理计划数量由治理项自动计算");
        }
        var responsibleUserId = request.responsibleUserId() == null
                || request.responsibleUserId().isBlank()
                ? request.assigneeId()
                : request.responsibleUserId();
        return service.createPlan(taskId, new CreatePlanCommand(
                0, request.title(), request.plannedStart(), request.plannedEnd(),
                responsibleUserId, request.dependencyIds(), request.issueIds()));
    }

    @PostMapping("/{taskId}/start")
    public GovernanceTaskResponse start(
            @PathVariable long taskId,
            @Valid @RequestBody StartTaskRequest request) {
        if (startService == null) throw new IllegalStateException("治理启动服务未配置");
        return GovernanceTaskResponse.from(
                startService.start(taskId, request.version(), request.actorUserId()));
    }

    @PatchMapping("/{taskId}/status")
    public GovernanceTaskResponse updateStatus(
            @PathVariable long taskId,
            @Valid @RequestBody UpdateTaskStatusRequest request) {
        return GovernanceTaskResponse.from(service.rejectTaskMutation(taskId));
    }

    @PostMapping("/{taskId}/submit-for-confirmation")
    public GovernanceTaskResponse submitForConfirmation(
            @PathVariable long taskId,
            @Valid @RequestBody SubmitForConfirmationRequest request) {
        return GovernanceTaskResponse.from(service.submitForConfirmation(taskId, request.version()));
    }

    public record CreateTaskRequest(
            @NotBlank String name,
            @NotEmpty List<Long> issueIds,
            @NotBlank String ownerUserId,
            @NotBlank String ownerName,
            @NotNull LocalDate dueDate) {}

    public record UpdatePlanRequest(@NotBlank String status) {}

    public record CreatePlanRequest(
            @NotBlank String title,
            LocalDate plannedStart,
            LocalDate plannedEnd,
            @Min(0) Integer plannedQuantity,
            @Min(0) Integer completedQuantity,
            String quantityUnit,
            String assigneeId,
            String responsibleUserId,
            List<Long> dependencyIds,
            List<Long> issueIds) {}

    public record StartTaskRequest(
            @Min(0) long version,
            @NotBlank String actorUserId) {}

    public record UpdateTaskStatusRequest(@NotBlank String status) {}

    public record SubmitForConfirmationRequest(@Min(0) long version) {}
}
