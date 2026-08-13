package com.tianshu.assets.system.infrastructure;

import com.tianshu.assets.system.domain.OperationLog;
import com.tianshu.assets.system.domain.OperationLogCriteria;
import com.tianshu.assets.system.domain.OperationLogStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("dev")
public class InMemoryOperationLogStore implements OperationLogStore {

    private final List<OperationLog> logs = new ArrayList<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public synchronized OperationLog append(OperationLog log) {
        var saved = new OperationLog(nextId.getAndIncrement(), log.actorUserId(), log.action(), log.targetType(),
                log.targetId(), log.detailJson(), log.createdAt());
        logs.add(saved);
        return saved;
    }

    @Override
    public synchronized List<OperationLog> query(OperationLogCriteria criteria) {
        var filtered = logs.stream().filter(log -> matches(log, criteria))
                .sorted(Comparator.comparing(OperationLog::createdAt).reversed())
                .toList();
        var offset = (long) (criteria.page() - 1) * criteria.perPage();
        return filtered.stream().skip(offset).limit(criteria.perPage()).toList();
    }

    @Override
    public synchronized long count(OperationLogCriteria criteria) {
        return logs.stream().filter(log -> matches(log, criteria)).count();
    }

    private boolean matches(OperationLog log, OperationLogCriteria criteria) {
        if (!criteria.actorUserId().isBlank() && !log.actorUserId().equals(criteria.actorUserId())) return false;
        if (!criteria.action().isBlank() && !log.action().equals(criteria.action())) return false;
        if (!criteria.targetType().isBlank() && !log.targetType().equals(criteria.targetType())) return false;
        if (criteria.from() != null && log.createdAt().isBefore(criteria.from())) return false;
        if (criteria.to() != null && log.createdAt().isAfter(criteria.to())) return false;
        return true;
    }
}
