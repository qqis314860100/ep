package com.tianshu.assets.governance.audit.application;

import com.tianshu.assets.governance.audit.domain.GovernanceAuditEvent;
import java.util.List;

public interface GovernanceAuditStore {

    GovernanceAuditEvent append(GovernanceAuditEvent event);

    List<GovernanceAuditEvent> history(long taskId);
}
