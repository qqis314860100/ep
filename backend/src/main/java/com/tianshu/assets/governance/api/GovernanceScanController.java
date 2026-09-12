package com.tianshu.assets.governance.api;

import com.tianshu.assets.governance.application.GovernanceAuthorizationService;
import com.tianshu.assets.governance.scan.application.GovernanceScanService;
import com.tianshu.assets.governance.scan.domain.GovernanceScanRun;
import com.tianshu.assets.governance.scan.domain.GovernanceScanTriggerType;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 治理扫描：读扫描轮次（登录即可——治理台首页要展示最近扫描），
 * 手工触发与重试（治理管理员——会全量扫库并生成治理问题）。
 */
@RestController
@RequestMapping("/api/v1/governance/scans")
public class GovernanceScanController {

    private final GovernanceScanService service;
    private final GovernanceAuthorizationService authorization;

    @Autowired
    public GovernanceScanController(
            GovernanceScanService service, GovernanceAuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @GetMapping
    public List<GovernanceScanRun> list(
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId) {
        authorization.requireAuthenticated(userId);
        return service.listRuns();
    }

    @GetMapping("/{id}")
    public GovernanceScanRun get(
            @PathVariable long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId) {
        authorization.requireAuthenticated(userId);
        return service.getRun(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public GovernanceScanRun scan(
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles) {
        authorization.requireGovernanceAdmin(userId, roles);
        return service.scan(GovernanceScanTriggerType.MANUAL, null);
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public GovernanceScanRun retry(
            @PathVariable long id,
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles) {
        authorization.requireGovernanceAdmin(userId, roles);
        return service.retry(id);
    }
}
