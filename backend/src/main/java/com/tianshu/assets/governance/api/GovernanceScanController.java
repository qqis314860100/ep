package com.tianshu.assets.governance.api;

import com.tianshu.assets.governance.scan.application.GovernanceScanService;
import com.tianshu.assets.governance.scan.domain.GovernanceScanRun;
import com.tianshu.assets.governance.scan.domain.GovernanceScanTriggerType;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/governance/scans")
public class GovernanceScanController {
    private final GovernanceScanService service;

    public GovernanceScanController(GovernanceScanService service) { this.service = service; }

    @GetMapping public List<GovernanceScanRun> list() { return service.listRuns(); }
    @GetMapping("/{id}") public GovernanceScanRun get(@PathVariable long id) { return service.getRun(id); }
    @PostMapping @ResponseStatus(HttpStatus.ACCEPTED)
    public GovernanceScanRun scan() { return service.scan(GovernanceScanTriggerType.MANUAL, null); }
    @PostMapping("/{id}/retry") @ResponseStatus(HttpStatus.ACCEPTED)
    public GovernanceScanRun retry(@PathVariable long id) { return service.retry(id); }
}
