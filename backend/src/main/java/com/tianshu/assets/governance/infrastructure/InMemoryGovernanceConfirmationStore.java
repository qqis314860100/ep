package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.governance.application.GovernanceConflictException;
import com.tianshu.assets.governance.application.GovernanceVersionConflictException;
import com.tianshu.assets.governance.confirmation.application.GovernanceConfirmationStore;
import com.tianshu.assets.governance.confirmation.domain.GovernanceConfirmationDecision;
import com.tianshu.assets.governance.confirmation.domain.GovernanceConfirmationRound;
import com.tianshu.assets.governance.confirmation.domain.GovernanceConfirmationRound.Status;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryGovernanceConfirmationStore implements GovernanceConfirmationStore {

    private final Map<Long, GovernanceConfirmationRound> rounds = new LinkedHashMap<>();
    private final Map<Long, GovernanceConfirmationDecision> decisions = new LinkedHashMap<>();
    private final AtomicLong nextRoundId = new AtomicLong(1);
    private final AtomicLong nextDecisionId = new AtomicLong(1);

    @Override
    public synchronized Optional<GovernanceConfirmationRound> currentRound(long taskId) {
        return rounds.values().stream()
                .filter(round -> round.taskId() == taskId)
                .max(Comparator.comparingInt(GovernanceConfirmationRound::governanceRound));
    }

    @Override
    public synchronized GovernanceConfirmationRound round(long roundId) {
        var round = rounds.get(roundId);
        if (round == null) throw new IllegalArgumentException("确认轮次不存在");
        return round;
    }

    @Override
    public synchronized GovernanceConfirmationRound createRound(
            long taskId, int governanceRound, Map<Long, Long> resultVersionIds, Instant createdAt) {
        if (currentRound(taskId).filter(round -> round.status() == Status.PENDING).isPresent()) {
            throw new GovernanceConflictException("当前确认轮次已存在");
        }
        var round = new GovernanceConfirmationRound(
                nextRoundId.getAndIncrement(), taskId, governanceRound, resultVersionIds,
                Status.PENDING, createdAt, null, 0);
        rounds.put(round.id(), round);
        return round;
    }

    @Override
    public synchronized void discardPendingRound(long roundId) {
        var round = rounds.get(roundId);
        if (round == null) return;
        if (round.status() != Status.PENDING
                || decisions.values().stream().anyMatch(decision -> decision.roundId() == roundId)) {
            throw new GovernanceConflictException("只能丢弃尚未使用的待确认轮次");
        }
        rounds.remove(roundId);
    }

    @Override
    public synchronized List<GovernanceConfirmationDecision> decisions(long roundId) {
        round(roundId);
        return decisions.values().stream()
                .filter(decision -> decision.roundId() == roundId)
                .sorted(Comparator.comparingLong(GovernanceConfirmationDecision::itemId))
                .toList();
    }

    @Override
    public synchronized GovernanceConfirmationDecision insertDecision(
            GovernanceConfirmationDecision decision) {
        return insertDecisions(List.of(decision)).getFirst();
    }

    @Override
    public synchronized List<GovernanceConfirmationDecision> insertDecisions(
            List<GovernanceConfirmationDecision> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new IllegalArgumentException("确认决定不能为空");
        }
        var keys = new HashSet<String>();
        for (var decision : requested) {
            round(decision.roundId());
            var key = decision.roundId() + ":" + decision.itemId();
            if (!keys.add(key) || decisions.values().stream().anyMatch(existing ->
                    existing.roundId() == decision.roundId() && existing.itemId() == decision.itemId())) {
                throw new GovernanceConflictException("本轮确认决定已保存，不能覆盖");
            }
        }
        return requested.stream().map(decision -> {
            var inserted = new GovernanceConfirmationDecision(
                    nextDecisionId.getAndIncrement(), decision.roundId(), decision.itemId(),
                    decision.resultVersionId(), decision.decision(), decision.comment(),
                    decision.confirmerUserId(), decision.decidedAt(), 0);
            decisions.put(inserted.id(), inserted);
            return inserted;
        }).toList();
    }

    @Override
    public synchronized GovernanceConfirmationRound completeRound(
            long roundId, long expectedVersion, Instant completedAt) {
        var current = round(roundId);
        if (current.status() != Status.PENDING) {
            throw new GovernanceConflictException("确认轮次已经完成");
        }
        if (current.version() != expectedVersion) {
            throw new GovernanceVersionConflictException("确认轮次已变化，请刷新后重试");
        }
        var completed = new GovernanceConfirmationRound(
                current.id(), current.taskId(), current.governanceRound(), current.resultVersionIds(),
                Status.COMPLETED, current.createdAt(), completedAt, current.version() + 1);
        rounds.put(roundId, completed);
        return completed;
    }
}
