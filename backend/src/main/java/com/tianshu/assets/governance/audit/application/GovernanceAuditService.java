package com.tianshu.assets.governance.audit.application;

import com.tianshu.assets.governance.audit.domain.GovernanceAuditEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GovernanceAuditService {

    private final GovernanceAuditStore store;
    private final Clock clock;

    @Autowired
    public GovernanceAuditService(GovernanceAuditStore store) {
        this(store, Clock.systemUTC());
    }

    public GovernanceAuditService(GovernanceAuditStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public GovernanceAuditEvent record(
            long taskId, Long itemId, String aggregateType, long aggregateId, String action,
            int governanceRound, String actorUserId, String beforeJson, String afterJson) {
        return store.append(new GovernanceAuditEvent(
                0, taskId, itemId, aggregateType, aggregateId, action, governanceRound,
                actorUserId, beforeJson, afterJson, Instant.now(clock)));
    }

    public List<GovernanceAuditEvent> history(long taskId) {
        return store.history(taskId);
    }
}
