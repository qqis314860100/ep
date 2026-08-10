package com.tianshu.assets.governance.operations.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceAcceptanceStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceConfirmationStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceDataStandardStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceEmployeeDirectory;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceIssueStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceScanRunStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceTaskStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GovernanceOperationsServiceTest {
    private GovernanceOperationsService service;

    @BeforeEach
    void setUp() {
        service = new GovernanceOperationsService(
                new InMemoryAssetRepository(), InMemoryGovernanceIssueStore.withFieldSeeds(),
                InMemoryGovernanceTaskStore.withLegacySeed(), new InMemoryGovernanceConfirmationStore(),
                new InMemoryGovernanceAcceptanceStore(), new InMemoryGovernanceScanRunStore(),
                new InMemoryGovernanceEmployeeDirectory(), new InMemoryGovernanceDataStandardStore(),
                Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void aggregatesOperationalFactsAndCadence() {
        var overview = service.overview(GovernanceOperationsService.Filter.empty());

        assertThat(overview.assetCount()).isEqualTo(5);
        assertThat(overview.openIssueCount()).isEqualTo(5);
        assertThat(overview.overdueTaskCount()).isEqualTo(1);
        assertThat(overview.metrics()).extracting(GovernanceOperationsService.Metric::key)
                .contains("responsibilityCoverage", "issueClosureCycle", "acceptancePassRate", "applicationSuccessRate");
        assertThat(overview.metrics().stream().filter(metric -> metric.key().equals("issueClosureCycle")).findFirst()
                .orElseThrow().available()).isFalse();
        assertThat(overview.cadences()).hasSize(4);
    }

    @Test
    void issueAndAssetFiltersNarrowTheOperationalFacts() {
        var overview = service.overview(new GovernanceOperationsService.Filter(
                "", "MISSING_DESCRIPTION", "", null, "", null, null));

        assertThat(overview.openIssueCount()).isEqualTo(2);
        assertThat(overview.issuesByType()).extracting(GovernanceOperationsService.Breakdown::key)
                .containsOnly("MISSING_DESCRIPTION");
    }
}
