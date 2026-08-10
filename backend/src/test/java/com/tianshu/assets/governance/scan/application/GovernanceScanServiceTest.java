package com.tianshu.assets.governance.scan.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianshu.assets.asset.infrastructure.InMemoryAssetRepository;
import com.tianshu.assets.dictionary.infrastructure.InMemoryDictionaryStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceDataStandardStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceEmployeeDirectory;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceIssueStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceMappingRuleStore;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceRuleCatalog;
import com.tianshu.assets.governance.infrastructure.InMemoryGovernanceScanRunStore;
import com.tianshu.assets.governance.issue.application.GovernanceIssueStore;
import com.tianshu.assets.governance.scan.domain.GovernanceScanRunStatus;
import com.tianshu.assets.governance.scan.domain.GovernanceScanTriggerType;
import com.tianshu.assets.governance.standard.domain.GovernanceDataStandard;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GovernanceScanServiceTest {
    private GovernanceIssueStore issueStore;
    private GovernanceScanService service;

    @BeforeEach
    void setUp() {
        issueStore = new InMemoryGovernanceIssueStore();
        var standards = new InMemoryGovernanceDataStandardStore();
        service = new GovernanceScanService(
                new InMemoryAssetRepository(), issueStore, new InMemoryGovernanceScanRunStore(), standards,
                new InMemoryGovernanceMappingRuleStore(), new InMemoryDictionaryStore(),
                new InMemoryGovernanceEmployeeDirectory(), new InMemoryGovernanceRuleCatalog(standards),
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void repeatedScanIsIdempotentAndRecordsRunCounts() {
        var first = service.scan(GovernanceScanTriggerType.MANUAL, null);
        var second = service.scan(GovernanceScanTriggerType.MANUAL, null);

        assertThat(first.status()).isEqualTo(GovernanceScanRunStatus.SUCCEEDED);
        assertThat(first.scannedAssetCount()).isEqualTo(5);
        assertThat(first.createdIssueCount()).isGreaterThan(0);
        assertThat(second.status()).isEqualTo(GovernanceScanRunStatus.SUCCEEDED);
        assertThat(second.createdIssueCount()).isZero();
        assertThat(second.unchangedIssueCount()).isEqualTo(first.createdIssueCount());
        assertThat(issueStore.find(null, null, null)).hasSize((int) first.createdIssueCount());
    }

    @Test
    void successfulRunCannotBeRetriedAsFailed() {
        var completed = service.scan(GovernanceScanTriggerType.MANUAL, null);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.retry(completed.id()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resolvedIssueIsReopenedWhenTheSameProblemAppearsAgain() {
        service.scan(GovernanceScanTriggerType.MANUAL, null);
        var issue = issueStore.find(null, null, null).getFirst();
        issueStore.claimOpen(List.of(issue), 99);
        var claimed = issueStore.findByIds(List.of(issue.id())).getFirst();
        issueStore.resolve(claimed.id(), claimed.version());

        var rerun = service.scan(GovernanceScanTriggerType.MANUAL, null);

        assertThat(rerun.reopenedIssueCount()).isEqualTo(1);
        assertThat(issueStore.findByIds(List.of(issue.id())).getFirst().status().name()).isEqualTo("OPEN");
    }

    @Test
    void failedRunCanRetryAfterTheBlockingConditionRecovers() {
        var standards = new ToggleDataStandardStore();
        var recoveringService = new GovernanceScanService(
                new InMemoryAssetRepository(), new InMemoryGovernanceIssueStore(),
                new InMemoryGovernanceScanRunStore(), standards,
                new InMemoryGovernanceMappingRuleStore(), new InMemoryDictionaryStore(),
                new InMemoryGovernanceEmployeeDirectory(), new InMemoryGovernanceRuleCatalog(standards),
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC));

        var failed = recoveringService.scan(GovernanceScanTriggerType.MANUAL, null);
        standards.available = true;
        var retried = recoveringService.retry(failed.id());

        assertThat(failed.status()).isEqualTo(GovernanceScanRunStatus.FAILED);
        assertThat(retried.status()).isEqualTo(GovernanceScanRunStatus.SUCCEEDED);
        assertThat(retried.triggerType()).isEqualTo(GovernanceScanTriggerType.RETRY);
        assertThat(retried.retryOfRunId()).isEqualTo(failed.id());
    }

    private static final class ToggleDataStandardStore extends InMemoryGovernanceDataStandardStore {
        private boolean available;

        @Override
        public List<GovernanceDataStandard> findAll() {
            return available ? super.findAll() : List.of();
        }
    }
}
