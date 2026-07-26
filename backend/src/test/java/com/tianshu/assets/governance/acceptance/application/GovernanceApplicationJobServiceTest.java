package com.tianshu.assets.governance.acceptance.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.tianshu.assets.asset.domain.AssetStatus;
import com.tianshu.assets.governance.support.GovernanceTestFixture;
import com.tianshu.assets.governance.task.domain.GovernanceTaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GovernanceApplicationJobServiceTest {

    private GovernanceTestFixture fixture;
    private GovernanceApplicationJobService service;

    @BeforeEach
    void setUp() {
        fixture = GovernanceTestFixture.fieldClosure();
        service = fixture.applicationJobService();
    }

    @Test
    void applyKeepsSuccessesAndRetriesOnlyFailedItems() {
        var job = fixture.acceptedTaskWithTwoItems();
        var itemIds = job.items().stream().map(item -> item.itemId()).toList();
        fixture.assetPort().failNextApplyFor(itemIds.get(1), "extension row locked");

        var first = service.run(job.id());

        assertThat(first.succeeded()).isEqualTo(1);
        assertThat(first.failed()).isEqualTo(1);
        assertThat(first.retryable()).isTrue();
        assertThat(first.errors()).containsEntry(itemIds.get(1), "extension row locked");

        var second = service.retry(job.id());

        assertThat(second.succeeded()).isEqualTo(2);
        assertThat(second.failed()).isZero();
        assertThat(fixture.assetPort().applyCount(itemIds.get(0))).isEqualTo(1);
        assertThat(fixture.assetPort().applyCount(itemIds.get(1))).isEqualTo(1);
    }

    @Test
    void taskCompletionDoesNotStandardizeAssetWithAnotherBlockingIssue() {
        var assetId = 101L;
        var job = fixture.acceptedJobFor(assetId);
        fixture.addOpenBlockingIssue(assetId, "MISSING_PRIMARY_FILE");

        var result = service.run(job.id());

        assertThat(result.failed()).isZero();
        assertThat(fixture.assetPort().status(assetId)).isEqualTo(AssetStatus.PENDING_CURATION);
        assertThat(fixture.taskStore().findById(job.taskId()).orElseThrow().status())
                .isEqualTo(GovernanceTaskStatus.COMPLETED);
    }
}
