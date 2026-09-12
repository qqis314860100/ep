package com.tianshu.assets.governance.api;

import com.tianshu.assets.governance.application.GovernanceAuthorizationService;
import com.tianshu.assets.governance.issue.application.GovernanceIssueService;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import com.tianshu.assets.governance.issue.domain.GovernanceIssue;
import com.tianshu.assets.governance.issue.domain.GovernanceIssueStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 治理问题池：读操作，登录即可（治理台首页要展示待认领问题）。 */
@RestController
@RequestMapping("/api/v1/governance/issues")
public class GovernanceIssueController {

    private final GovernanceIssueService service;
    private final GovernanceAuthorizationService authorization;

    @Autowired
    public GovernanceIssueController(
            GovernanceIssueService service, GovernanceAuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @GetMapping
    public List<GovernanceIssueResponse> list(
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId,
            @RequestParam(required = false) GovernanceField field,
            @RequestParam(required = false) GovernanceIssueStatus status,
            @RequestParam(required = false) Long assetId) {
        authorization.requireAuthenticated(userId);
        return service.list(field, status, assetId).stream().map(GovernanceIssueResponse::from).toList();
    }

    public record GovernanceIssueResponse(
            long id,
            long assetId,
            GovernanceField targetField,
            String issueType,
            String targetPath,
            String originalFactJson,
            String severity,
            boolean blocking,
            GovernanceIssueStatus status,
            Long taskId,
            long version,
            Instant createdAt,
            Instant updatedAt) {

        static GovernanceIssueResponse from(GovernanceIssue issue) {
            return new GovernanceIssueResponse(
                    issue.id(), issue.assetId(), issue.targetField(), issue.issueType(), issue.targetPath(),
                    issue.originalFactJson(), issue.severity(), issue.blocking(), issue.status(),
                    issue.taskId(), issue.version(), issue.createdAt(), issue.updatedAt());
        }
    }
}
