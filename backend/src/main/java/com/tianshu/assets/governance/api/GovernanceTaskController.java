package com.tianshu.assets.governance.api;

import com.tianshu.assets.governance.application.GovernanceTaskService;
import com.tianshu.assets.governance.domain.GovernanceEmployee;
import com.tianshu.assets.governance.domain.GovernancePlan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

    private final GovernanceTaskService service;

    public GovernanceTaskController(GovernanceTaskService service) {
        this.service = service;
    }

    @GetMapping
    public List<GovernanceTaskResponse> list() {
        return service.list().stream().map(task -> GovernanceTaskResponse.from(task, service.assigneeId(task.id()))).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GovernanceTaskResponse create(@Valid @RequestBody CreateTaskRequest request) {
        var task = service.create(request.name(), request.scope(), request.owner(), request.total(), request.dueDate(), request.assigneeId());
        return GovernanceTaskResponse.from(task, service.assigneeId(task.id()));
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
        return service.updatePlan(taskId, planId, request.status());
    }

    @PostMapping("/{taskId}/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public GovernancePlan createPlan(
            @PathVariable long taskId,
            @Valid @RequestBody CreatePlanRequest request) {
        return service.createPlan(taskId, request.title(), request.plannedStart(), request.plannedEnd(),
                request.plannedQuantity(), request.quantityUnit(), request.assigneeId(), request.dependencyIds());
    }

    @PatchMapping("/{taskId}/progress")
    public GovernanceTaskResponse updateProgress(
            @PathVariable long taskId,
            @Valid @RequestBody UpdateProgressRequest request) {
        var task = service.updateProgress(taskId, request.completed());
        return GovernanceTaskResponse.from(task, service.assigneeId(task.id()));
    }

    public record CreateTaskRequest(
            @NotBlank String name,
            String scope,
            @NotBlank String owner,
            @Min(1) int total,
            @NotNull LocalDate dueDate,
            String assigneeId) {}

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
}
