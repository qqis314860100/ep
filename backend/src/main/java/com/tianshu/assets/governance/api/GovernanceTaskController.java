package com.tianshu.assets.governance.api;

import com.tianshu.assets.governance.domain.GovernanceEmployee;
import com.tianshu.assets.governance.issue.application.GovernanceIssueService;
import com.tianshu.assets.governance.issue.application.GovernanceIssueService.CreateGovernanceTaskCommand;
import com.tianshu.assets.governance.task.application.GovernanceTaskApplicationService;
import com.tianshu.assets.governance.task.domain.GovernancePlan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/governance/tasks")
public class GovernanceTaskController {

    private final GovernanceTaskApplicationService service;
    private final GovernanceIssueService issueService;

    public GovernanceTaskController(
            GovernanceTaskApplicationService service, GovernanceIssueService issueService) {
        this.service = service;
        this.issueService = issueService;
    }

    @GetMapping
    public List<GovernanceTaskResponse> list() {
        return service.list().stream().map(GovernanceTaskResponse::from).toList();
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
    public List<GovernancePlan> plans(@PathVariable long taskId) {
        return service.plans(taskId);
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
        return service.rejectPlanMutation(taskId);
    }

    @PatchMapping("/{taskId}/progress")
    public GovernanceTaskResponse updateProgress(
            @PathVariable long taskId,
            @Valid @RequestBody UpdateProgressRequest request) {
        return GovernanceTaskResponse.from(service.rejectTaskMutation(taskId));
    }

    @PatchMapping("/{taskId}/status")
    public GovernanceTaskResponse updateStatus(
            @PathVariable long taskId,
            @Valid @RequestBody UpdateTaskStatusRequest request) {
        return GovernanceTaskResponse.from(service.rejectTaskMutation(taskId));
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
            @Min(0) int plannedQuantity,
            String quantityUnit,
            String assigneeId,
            List<Long> dependencyIds) {}

    public record UpdateProgressRequest(@Min(0) int completed) {}

    public record UpdateTaskStatusRequest(@NotBlank String status) {}
}
