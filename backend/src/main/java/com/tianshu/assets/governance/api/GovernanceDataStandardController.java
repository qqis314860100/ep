package com.tianshu.assets.governance.api;

import com.tianshu.assets.asset.domain.AssetType;
import com.tianshu.assets.governance.application.GovernanceAuthorizationService;
import com.tianshu.assets.governance.standard.application.GovernanceDataStandardService;
import com.tianshu.assets.governance.standard.domain.GovernanceDataStandard;
import com.tianshu.assets.governance.standard.domain.GovernanceStandardImpactReview;
import com.tianshu.assets.governance.standard.domain.GovernanceStandardRule;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 治理数据标准：读标准与影响评估（登录即可），
 * 建标准/出新版本/启用/停用（治理管理员——属治理配置，启用会改变全量资产的治理口径）。
 */
@RestController
@RequestMapping("/api/v1/governance/standards")
public class GovernanceDataStandardController {

    private final GovernanceDataStandardService service;
    private final GovernanceAuthorizationService authorization;

    @Autowired
    public GovernanceDataStandardController(
            GovernanceDataStandardService service, GovernanceAuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @GetMapping
    public List<GovernanceDataStandard> list(
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId) {
        authorization.requireAuthenticated(userId);
        return service.list();
    }

    @GetMapping("/{id}")
    public GovernanceDataStandard get(
            @PathVariable long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId) {
        authorization.requireAuthenticated(userId);
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GovernanceDataStandard create(
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles,
            @RequestBody CreateStandardRequest request) {
        authorization.requireGovernanceAdmin(userId, roles);
        return service.create(request.toCommand());
    }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public GovernanceDataStandard createVersion(
            @PathVariable long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles,
            @RequestBody CreateVersionRequest request) {
        authorization.requireGovernanceAdmin(userId, roles);
        return service.createVersion(id, request.toCommand());
    }

    @PostMapping("/{id}/enable")
    public GovernanceDataStandardService.ActivationResult enable(
            @PathVariable long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles,
            @RequestBody VersionRequest request) {
        authorization.requireGovernanceAdmin(userId, roles);
        return service.enable(id, request.version());
    }

    @PostMapping("/{id}/disable")
    public GovernanceDataStandard disable(
            @PathVariable long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles,
            @RequestBody VersionRequest request) {
        authorization.requireGovernanceAdmin(userId, roles);
        return service.disable(id, request.version());
    }

    @GetMapping("/{id}/impact-reviews")
    public List<GovernanceStandardImpactReview> impactReviews(
            @PathVariable long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId) {
        authorization.requireAuthenticated(userId);
        return service.impactReviews(id);
    }

    public record CreateStandardRequest(
            String standardCode, long standardVersion, String name,
            List<AssetType> applicableAssetTypes, String ownerUserId, String ownerName,
            String changeSummary, List<GovernanceStandardRule> rules) {
        GovernanceDataStandardService.CreateCommand toCommand() {
            return new GovernanceDataStandardService.CreateCommand(
                    standardCode, standardVersion, name, applicableAssetTypes, ownerUserId, ownerName,
                    changeSummary, rules);
        }
    }

    public record CreateVersionRequest(
            long standardVersion, String name, List<AssetType> applicableAssetTypes,
            String ownerUserId, String ownerName, String changeSummary,
            List<GovernanceStandardRule> rules) {
        GovernanceDataStandardService.CreateVersionCommand toCommand() {
            return new GovernanceDataStandardService.CreateVersionCommand(
                    standardVersion, name, applicableAssetTypes, ownerUserId, ownerName, changeSummary, rules);
        }
    }

    public record VersionRequest(long version) {}
}
