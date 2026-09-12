package com.tianshu.assets.governance.api;

import com.tianshu.assets.governance.acceptance.application.GovernanceApplicationJobService;
import com.tianshu.assets.governance.application.GovernanceAuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 治理结果应用作业：读作业状态（登录即可），重试（治理管理员）。 */
@RestController
@RequestMapping("/api/v1/governance/jobs")
public class GovernanceJobController {

    private final GovernanceApplicationJobService service;
    private final GovernanceAuthorizationService authorization;

    @Autowired
    public GovernanceJobController(
            GovernanceApplicationJobService service, GovernanceAuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @GetMapping("/{jobId}")
    public GovernanceApplicationJobService.JobSummary get(
            @PathVariable long jobId,
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId) {
        authorization.requireAuthenticated(userId);
        return service.get(jobId);
    }

    @PostMapping("/{jobId}/retry")
    public GovernanceApplicationJobService.JobSummary retry(
            @PathVariable long jobId,
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId,
            @RequestHeader(name = "X-User-Roles", defaultValue = "") String roles) {
        authorization.requireGovernanceAdmin(userId, roles);
        return service.retry(jobId);
    }
}
