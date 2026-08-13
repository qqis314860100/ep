package com.tianshu.assets.system.api;

import com.tianshu.assets.system.application.SystemAdminService;
import com.tianshu.assets.system.domain.OperationLogCriteria;
import com.tianshu.assets.system.domain.SystemRole;
import com.tianshu.assets.system.domain.SystemUserScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class SystemAdminController {

    private final SystemAdminService service;

    public SystemAdminController(SystemAdminService service) {
        this.service = service;
    }

    @GetMapping("/users")
    public List<SystemUserResponse> users() {
        return service.users().stream().map(SystemUserResponse::from).toList();
    }

    @PatchMapping("/users/{id}/roles")
    public SystemUserResponse updateRoles(
            @PathVariable @Positive long id,
            @Valid @RequestBody UpdateRolesRequest request,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String operator) {
        return SystemUserResponse.from(service.updateRoles(id, request.roles(), operator, request.version()));
    }

    @PatchMapping("/users/{id}/scopes")
    public SystemUserResponse updateScopes(
            @PathVariable @Positive long id,
            @Valid @RequestBody UpdateScopesRequest request,
            @RequestHeader(name = "X-User-Id", defaultValue = "demo-user") String operator) {
        return SystemUserResponse.from(service.updateScopes(id, request.scopes(), operator, request.version()));
    }

    @GetMapping("/operation-logs")
    public OperationLogPageResponse logs(
            @RequestParam(name = "actor", defaultValue = "") String actorUserId,
            @RequestParam(name = "action", defaultValue = "") String action,
            @RequestParam(name = "target_type", defaultValue = "") String targetType,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(name = "per_page", defaultValue = "20") @Min(1) @Max(100) int perPage) {
        var criteria = new OperationLogCriteria(actorUserId, action, targetType, from, to, page, perPage);
        var data = service.logs(criteria).stream().map(OperationLogResponse::from).toList();
        var total = service.logCount(criteria);
        return new OperationLogPageResponse(data, new PageMeta(total, page, perPage));
    }

    public record UpdateRolesRequest(@NotNull Set<SystemRole> roles, long version) {}

    public record UpdateScopesRequest(List<SystemUserScope> scopes, long version) {}

    public record OperationLogPageResponse(List<OperationLogResponse> data, PageMeta meta) {}

    public record PageMeta(long total, int page, int perPage) {}
}
