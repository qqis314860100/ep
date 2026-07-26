package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.governance.audit.application.GovernanceAuditStore;
import com.tianshu.assets.governance.audit.domain.GovernanceAuditEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryGovernanceAuditStore implements GovernanceAuditStore {

    private final AtomicLong nextId = new AtomicLong(1);
    private final List<GovernanceAuditEvent> events = new ArrayList<>();

    @Override
    public synchronized GovernanceAuditEvent append(GovernanceAuditEvent requested) {
        var event = new GovernanceAuditEvent(
                nextId.getAndIncrement(), requested.taskId(), requested.itemId(), requested.aggregateType(),
                requested.aggregateId(), requested.action(), requested.governanceRound(), requested.actorUserId(),
                requested.beforeJson(), requested.afterJson(), requested.createdAt());
        events.add(event);
        return event;
    }

    @Override
    public synchronized List<GovernanceAuditEvent> history(long taskId) {
        return events.stream().filter(event -> event.taskId() == taskId)
                .sorted(Comparator.comparing(GovernanceAuditEvent::createdAt)
                        .thenComparingLong(GovernanceAuditEvent::id))
                .toList();
    }
}
