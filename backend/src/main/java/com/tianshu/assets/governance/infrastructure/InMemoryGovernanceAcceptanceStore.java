package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.governance.acceptance.application.GovernanceAcceptanceStore;
import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceMetricResult;
import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceRound;
import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceSample;
import com.tianshu.assets.governance.acceptance.domain.GovernanceOperationJob;
import com.tianshu.assets.governance.acceptance.domain.GovernanceOperationJobItem;
import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryGovernanceAcceptanceStore implements GovernanceAcceptanceStore {

    private final Map<Long, GovernanceAcceptanceRound> rounds = new LinkedHashMap<>();
    private final Map<Long, GovernanceOperationJob> applicationJobs = new LinkedHashMap<>();
    private final AtomicLong nextRoundId = new AtomicLong(1);
    private final AtomicLong nextMetricId = new AtomicLong(1);
    private final AtomicLong nextSampleId = new AtomicLong(1);
    private final AtomicLong nextJobId = new AtomicLong(1);

    @Override
    public synchronized Optional<GovernanceAcceptanceRound> currentRound(long taskId) {
        return rounds.values().stream().filter(round -> round.taskId() == taskId)
                .max(Comparator.comparingInt(GovernanceAcceptanceRound::governanceRound));
    }

    @Override
    public synchronized GovernanceAcceptanceRound round(long roundId) {
        var round = rounds.get(roundId);
        if (round == null) throw new IllegalArgumentException("验收轮次不存在");
        return round;
    }

    @Override
    public synchronized GovernanceAcceptanceRound createRound(GovernanceAcceptanceRound requested) {
        if (currentRound(requested.taskId()).filter(round ->
                round.governanceRound() == requested.governanceRound()).isPresent()) {
            throw new GovernanceConflictException("当前治理轮次已存在验收记录");
        }
        var id = nextRoundId.getAndIncrement();
        var metrics = requested.metricResults().stream().map(result -> new GovernanceAcceptanceMetricResult(
                nextMetricId.getAndIncrement(), id, result.metric(), result.numerator(), result.denominator(),
                result.value(), result.threshold(), result.applicability(), result.passed(),
                result.affectedItemIds(), 0)).toList();
        var samples = requested.samples().stream().map(sample -> new GovernanceAcceptanceSample(
                nextSampleId.getAndIncrement(), id, sample.itemId(), sample.passed(), sample.issueDescription(),
                sample.reviewerUserId(), sample.checkedAt(), 0)).toList();
        var created = new GovernanceAcceptanceRound(
                id, requested.taskId(), requested.governanceRound(), requested.policy(), metrics, samples,
                requested.status(), requested.createdAt(), requested.completedAt(), 0);
        rounds.put(id, created);
        return created;
    }

    @Override
    public synchronized GovernanceAcceptanceRound updateRound(
            GovernanceAcceptanceRound requested, long expectedVersion) {
        var current = round(requested.id());
        if (current.version() != expectedVersion) {
            throw new GovernanceVersionConflictException("验收轮次已变化，请刷新后重试");
        }
        var updated = new GovernanceAcceptanceRound(
                current.id(), current.taskId(), current.governanceRound(), current.policy(),
                requested.metricResults(), requested.samples(), requested.status(), current.createdAt(),
                requested.completedAt(), expectedVersion + 1);
        rounds.put(updated.id(), updated);
        return updated;
    }

    @Override
    public synchronized GovernanceOperationJob createApplicationJob(
            long taskId,
            long acceptanceRoundId,
            Map<Long, Long> resultVersionIds,
            String requestedBy,
            java.time.Instant requestedAt) {
        var existing = applicationJobs.values().stream()
                .filter(job -> job.acceptanceRoundId() == acceptanceRoundId)
                .findFirst();
        if (existing.isPresent()) return existing.orElseThrow();
        var items = resultVersionIds.entrySet().stream()
                .map(entry -> new GovernanceOperationJobItem(
                        entry.getKey(), entry.getValue(), GovernanceOperationJobItem.Status.PENDING, ""))
                .toList();
        var created = new GovernanceOperationJob(
                nextJobId.getAndIncrement(), taskId, acceptanceRoundId, items,
                requestedBy, requestedAt, GovernanceOperationJob.Status.PENDING, 0);
        applicationJobs.put(created.id(), created);
        return created;
    }

    @Override
    public synchronized Optional<GovernanceOperationJob> applicationJob(long jobId) {
        return Optional.ofNullable(applicationJobs.get(jobId));
    }

    @Override
    public synchronized GovernanceOperationJob claimApplicationJob(long jobId, long expectedVersion) {
        var current = applicationJob(jobId)
                .orElseThrow(() -> new IllegalArgumentException("治理应用作业不存在"));
        if (current.version() != expectedVersion || current.status() == GovernanceOperationJob.Status.RUNNING) {
            throw new GovernanceVersionConflictException("治理应用作业已被其他请求处理");
        }
        var claimed = new GovernanceOperationJob(
                current.id(), current.taskId(), current.acceptanceRoundId(), current.items(),
                current.requestedBy(), current.requestedAt(), GovernanceOperationJob.Status.RUNNING,
                current.version() + 1);
        applicationJobs.put(jobId, claimed);
        return claimed;
    }

    @Override
    public synchronized GovernanceOperationJob updateApplicationJob(
            GovernanceOperationJob requested, long expectedVersion) {
        var current = applicationJob(requested.id())
                .orElseThrow(() -> new IllegalArgumentException("治理应用作业不存在"));
        if (current.version() != expectedVersion) {
            throw new GovernanceVersionConflictException("治理应用作业已变化，请刷新后重试");
        }
        var updated = new GovernanceOperationJob(
                current.id(), current.taskId(), current.acceptanceRoundId(), requested.items(),
                current.requestedBy(), current.requestedAt(), requested.status(), current.version() + 1);
        applicationJobs.put(updated.id(), updated);
        return updated;
    }
}
