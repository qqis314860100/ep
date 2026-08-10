package com.tianshu.assets.governance.scan.application;

import com.tianshu.assets.governance.scan.domain.GovernanceScanTriggerType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "asset.governance-scan.enabled", havingValue = "true")
public class GovernanceScanScheduler {
    private final GovernanceScanService service;
    public GovernanceScanScheduler(GovernanceScanService service) { this.service = service; }

    @Scheduled(fixedDelayString = "${asset.governance-scan.fixed-delay-ms:86400000}")
    public void runDailyScan() { service.scan(GovernanceScanTriggerType.SCHEDULED, null); }
}
