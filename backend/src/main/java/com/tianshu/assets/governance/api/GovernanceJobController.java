package com.tianshu.assets.governance.api;

import com.tianshu.assets.governance.acceptance.application.GovernanceApplicationJobService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/governance/jobs")
public class GovernanceJobController {

    private final GovernanceApplicationJobService service;

    public GovernanceJobController(GovernanceApplicationJobService service) {
        this.service = service;
    }

    @GetMapping("/{jobId}")
    public GovernanceApplicationJobService.JobSummary get(@PathVariable long jobId) {
        return service.get(jobId);
    }

    @PostMapping("/{jobId}/retry")
    public GovernanceApplicationJobService.JobSummary retry(@PathVariable long jobId) {
        return service.retry(jobId);
    }
}
