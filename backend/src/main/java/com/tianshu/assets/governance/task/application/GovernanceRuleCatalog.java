package com.tianshu.assets.governance.task.application;

import com.tianshu.assets.governance.task.domain.GovernanceRuleSnapshot;

public interface GovernanceRuleCatalog {

    GovernanceRuleSnapshot enabledSnapshot();
}
