package com.tianshu.assets.governance.api;

import com.tianshu.assets.governance.issue.application.GovernanceIssueService;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import com.tianshu.assets.governance.issue.domain.GovernanceIssue;
import com.tianshu.assets.governance.issue.domain.GovernanceIssueStatus;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/governance/issues")
public class GovernanceIssueController {

    private final GovernanceIssueService service;

    public GovernanceIssueController(GovernanceIssueService service) {
        this.service = service;
    }

    @GetMapping
    public List<GovernanceIssueResponse> list(
            @RequestParam(required = false) GovernanceField field,
            @RequestParam(required = false) GovernanceIssueStatus status,
            @RequestParam(required = false) Long assetId) {
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
            long version) {

        static GovernanceIssueResponse from(GovernanceIssue issue) {
            return new GovernanceIssueResponse(
                    issue.id(), issue.assetId(), issue.targetField(), issue.issueType(), issue.targetPath(),
                    issue.originalFactJson(), issue.severity(), issue.blocking(), issue.status(),
                    issue.taskId(), issue.version());
        }
    }
}
