package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.governance.acceptance.application.GovernanceAcceptanceStore;
import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceMetricResult;
import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceRound;
import com.tianshu.assets.governance.acceptance.domain.GovernanceAcceptanceSample;
import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryGovernanceAcceptanceStore implements GovernanceAcceptanceStore {

    private final Map<Long, GovernanceAcceptanceRound> rounds = new LinkedHashMap<>();
    private final AtomicLong nextRoundId = new AtomicLong(1);
    private final AtomicLong nextMetricId = new AtomicLong(1);
    private final AtomicLong nextSampleId = new AtomicLong(1);

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
}
