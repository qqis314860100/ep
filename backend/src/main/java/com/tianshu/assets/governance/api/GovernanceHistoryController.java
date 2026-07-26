package com.tianshu.assets.governance.api;

import com.tianshu.assets.governance.audit.application.GovernanceAuditService;
import com.tianshu.assets.governance.audit.application.GovernanceReportService;
import com.tianshu.assets.governance.audit.domain.GovernanceAuditEvent;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/governance/tasks/{taskId}")
public class GovernanceHistoryController {

    private final GovernanceAuditService auditService;
    private final GovernanceReportService reportService;

    public GovernanceHistoryController(
            GovernanceAuditService auditService, GovernanceReportService reportService) {
        this.auditService = auditService;
        this.reportService = reportService;
    }

    @GetMapping("/history")
    public List<GovernanceAuditEvent> history(@PathVariable long taskId) {
        return auditService.history(taskId);
    }

    @GetMapping("/report")
    public GovernanceReportService.GovernanceReport report(@PathVariable long taskId) {
        return reportService.report(taskId);
    }
}
