package com.tianshu.assets.governance.api;

import com.tianshu.assets.governance.application.GovernanceAuthorizationService;
import com.tianshu.assets.governance.audit.application.GovernanceAuditService;
import com.tianshu.assets.governance.audit.application.GovernanceReportService;
import com.tianshu.assets.governance.audit.domain.GovernanceAuditEvent;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 治理任务审计轨迹与验收报告：读操作，登录即可。 */
@RestController
@RequestMapping("/api/v1/governance/tasks/{taskId}")
public class GovernanceHistoryController {

    private final GovernanceAuditService auditService;
    private final GovernanceReportService reportService;
    private final GovernanceAuthorizationService authorization;

    @Autowired
    public GovernanceHistoryController(
            GovernanceAuditService auditService,
            GovernanceReportService reportService,
            GovernanceAuthorizationService authorization) {
        this.auditService = auditService;
        this.reportService = reportService;
        this.authorization = authorization;
    }

    @GetMapping("/history")
    public List<GovernanceAuditEvent> history(
            @PathVariable long taskId,
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId) {
        authorization.requireAuthenticated(userId);
        return auditService.history(taskId);
    }

    @GetMapping("/report")
    public GovernanceReportService.GovernanceReport report(
            @PathVariable long taskId,
            @RequestHeader(name = "X-User-Id", defaultValue = "") String userId) {
        authorization.requireAuthenticated(userId);
        return reportService.report(taskId);
    }
}
