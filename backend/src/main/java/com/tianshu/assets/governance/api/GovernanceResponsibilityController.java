package com.tianshu.assets.governance.api;

import com.tianshu.assets.governance.application.GovernanceAuthorizationService;
import com.tianshu.assets.governance.application.GovernanceResponsibilityService;
import com.tianshu.assets.governance.confirmation.application.AssetResponsibilityPort.AssetResponsibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资产责任人指派。内容管理员 / 系统管理员为资产指派当前有效责任人，
 * 使新建资产可进入治理闭环的业务确认环节。
 */
@Validated
@RestController
@RequestMapping("/api/v1/governance/asset-responsibilities")
public class GovernanceResponsibilityController {

    private final GovernanceResponsibilityService service;
    private final GovernanceAuthorizationService authorizationService;

    @Autowired
    public GovernanceResponsibilityController(
            GovernanceResponsibilityService service,
            GovernanceAuthorizationService authorizationService) {
        this.service = service;
        this.authorizationService = authorizationService;
    }

    @PutMapping("/{assetId}")
    public AssetResponsibilityResponse assign(
            @PathVariable @Min(1) long assetId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles,
            @Valid @RequestBody AssignRequest request) {
        authorizationService.requireGovernanceAdmin(roles);
        return AssetResponsibilityResponse.from(service.assign(
                assetId, request.responsibleUserId(), request.responsibilityScope()));
    }

    @GetMapping("/{assetId}")
    public AssetResponsibilityResponse current(
            @PathVariable @Min(1) long assetId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles) {
        authorizationService.requireGovernanceAdmin(roles);
        return AssetResponsibilityResponse.from(service.current(assetId));
    }

    public record AssignRequest(
            @NotBlank(message = "责任人不能为空") String responsibleUserId,
            @NotBlank(message = "责任范围不能为空") String responsibilityScope) {}

    public record AssetResponsibilityResponse(
            long assetId, String responsibleUserId, String responsibilityScope) {
        static AssetResponsibilityResponse from(AssetResponsibility responsibility) {
            return new AssetResponsibilityResponse(
                    responsibility.assetId(), responsibility.responsibleUserId(),
                    responsibility.responsibilityScope());
        }
    }
}
