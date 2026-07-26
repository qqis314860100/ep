package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.governance.task.application.GovernanceRuleCatalog;
import com.tianshu.assets.governance.task.domain.GovernanceRuleSnapshot;
import java.util.Map;

public class InMemoryGovernanceRuleCatalog implements GovernanceRuleCatalog {

    private final GovernanceRuleSnapshot enabledSnapshot;

    public InMemoryGovernanceRuleCatalog() {
        this(new GovernanceRuleSnapshot(
                0, "FIELD-COMPLETENESS", 1, 1,
                Map.of("specialty", 5L, "scope", 8L), "FIELD-QUALITY", 2));
    }

    public InMemoryGovernanceRuleCatalog(GovernanceRuleSnapshot enabledSnapshot) {
        this.enabledSnapshot = enabledSnapshot;
    }

    @Override
    public GovernanceRuleSnapshot enabledSnapshot() {
        return new GovernanceRuleSnapshot(
                enabledSnapshot.id(), enabledSnapshot.dataStandardId(), enabledSnapshot.dataStandardVersion(),
                enabledSnapshot.fieldRuleVersion(), enabledSnapshot.dictionaryVersions(),
                enabledSnapshot.qualityPolicyId(), enabledSnapshot.qualityPolicyVersion());
    }
}
