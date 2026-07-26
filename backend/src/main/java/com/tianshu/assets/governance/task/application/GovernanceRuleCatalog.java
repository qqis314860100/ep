package com.tianshu.assets.governance.task.application;

import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.governance.task.domain.GovernanceRuleSnapshot;
import java.util.List;

public interface GovernanceRuleCatalog {

    GovernanceRuleSnapshot enabledSnapshot();

    default List<AssetScope> validScopes() {
        return List.of();
    }
}
