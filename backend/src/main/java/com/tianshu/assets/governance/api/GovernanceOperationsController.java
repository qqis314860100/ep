package com.tianshu.assets.governance.api;

import com.tianshu.assets.asset.domain.AssetType;
import com.tianshu.assets.governance.application.GovernanceAuthorizationService;
import com.tianshu.assets.governance.operations.application.GovernanceOperationsService;
import com.tianshu.assets.governance.operations.application.GovernanceOperationsService.Filter;
import com.tianshu.assets.governance.operations.application.GovernanceOperationsService.Overview;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 治理运营看板：读操作，登录即可。 */
@RestController
@RequestMapping("/api/v1/governance/operations")
public class GovernanceOperationsController {

    private final GovernanceOperationsService service;
    private final GovernanceAuthorizationService authorization;

    @Autowired
    public GovernanceOperationsController(
            GovernanceOperationsService service, GovernanceAuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @GetMapping("/overview")
    public Overview overview(
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId,
            @RequestParam(required = false) String standardCode,
            @RequestParam(required = false) String issueType,
            @RequestParam(required = false) String ownerUserId,
            @RequestParam(required = false) AssetType assetType,
            @RequestParam(required = false) String base,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        authorization.requireAuthenticated(userId);
        return service.overview(new Filter(
                standardCode, issueType, ownerUserId, assetType, base, fromDate, toDate));
    }
}
