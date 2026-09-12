package com.tianshu.assets.governance.api;

import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.governance.application.GovernanceAuthorizationService;
import com.tianshu.assets.governance.mapping.application.GovernanceMappingRuleService;
import com.tianshu.assets.governance.mapping.domain.GovernanceMappingRule;
import com.tianshu.assets.governance.mapping.domain.GovernanceMappingStatus;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 治理映射规则：读规则与版本历史（登录即可），
 * 建规则/出新版本/确认/停用（治理管理员——属治理配置，会改变全量资产的映射口径）。
 */
@RestController
@RequestMapping("/api/v1/governance/mappings")
public class GovernanceMappingRuleController {

    private final GovernanceMappingRuleService service;
    private final GovernanceAuthorizationService authorization;

    @Autowired
    public GovernanceMappingRuleController(
            GovernanceMappingRuleService service, GovernanceAuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @GetMapping
    public List<GovernanceMappingRule> list(
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId,
            @RequestParam(required = false) GovernanceMappingStatus status,
            @RequestParam(required = false) String sourceDimension,
            @RequestParam(required = false) String query) {
        authorization.requireAuthenticated(userId);
        return service.list(status, sourceDimension, query);
    }

    @GetMapping("/{id}")
    public GovernanceMappingRule get(
            @PathVariable long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId) {
        authorization.requireAuthenticated(userId);
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GovernanceMappingRule create(
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles,
            @RequestBody CreateRequest request) {
        authorization.requireGovernanceAdmin(userId, roles);
        return service.create(request.toCommand());
    }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public GovernanceMappingRule version(
            @PathVariable long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles,
            @RequestBody VersionRequest request) {
        authorization.requireGovernanceAdmin(userId, roles);
        return service.createVersion(id, request.toCommand());
    }

    @PostMapping("/{id}/confirm")
    public GovernanceMappingRule confirm(
            @PathVariable long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles,
            @RequestBody ConfirmRequest request) {
        authorization.requireGovernanceAdmin(userId, roles);
        return service.confirm(id, new GovernanceMappingRuleService.ConfirmCommand(
                request.version(), request.userId(), request.userName(), request.comment()));
    }

    @PostMapping("/{id}/disable")
    public GovernanceMappingRule disable(
            @PathVariable long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles,
            @RequestBody VersionRequest request) {
        authorization.requireGovernanceAdmin(userId, roles);
        return service.disable(id, request.version());
    }

    @GetMapping("/{id}/history")
    public List<GovernanceMappingRule> history(
            @PathVariable long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId) {
        authorization.requireAuthenticated(userId);
        var current = service.get(id);
        return service.list(null, current.sourceDimension(), null).stream()
                .filter(item -> item.standardId() == current.standardId()
                        && item.standardVersion() == current.standardVersion()
                        && item.sourceValue().equals(current.sourceValue())
                        && item.scope().equals(current.scope()))
                .toList();
    }

    public record CreateRequest(
            long standardId, String sourceDimension, String sourceValue,
            String targetDictionaryCategory, long targetDictionaryItemId, AssetScope scope,
            boolean ambiguous, long affectedAssetCount) {
        GovernanceMappingRuleService.CreateCommand toCommand() {
            return new GovernanceMappingRuleService.CreateCommand(
                    standardId, sourceDimension, sourceValue, targetDictionaryCategory,
                    targetDictionaryItemId, scope, ambiguous, affectedAssetCount);
        }
    }

    public record VersionRequest(
            long standardId, long standardVersion, String sourceDimension, String sourceValue,
            String targetDictionaryCategory, long targetDictionaryItemId, AssetScope scope,
            boolean ambiguous, long affectedAssetCount, long version) {
        GovernanceMappingRuleService.VersionCommand toCommand() {
            return new GovernanceMappingRuleService.VersionCommand(
                    standardId, standardVersion, sourceDimension, sourceValue,
                    targetDictionaryCategory, targetDictionaryItemId, scope, ambiguous,
                    affectedAssetCount);
        }
    }

    public record ConfirmRequest(long version, String userId, String userName, String comment) {}
}
