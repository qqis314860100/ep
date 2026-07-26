package com.tianshu.assets.governance.execution.application;

import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.governance.issue.domain.GovernanceField;
import com.tianshu.assets.governance.task.domain.GovernanceRuleSnapshot;
import java.util.List;

public interface GovernanceActionHandler {

    void validate(GovernanceField field, String proposedValueJson, ValidationContext context);

    record ValidationContext(
            GovernanceRuleSnapshot frozenRules,
            GovernanceRuleSnapshot enabledRules,
            List<AssetScope> validScopes) {
        public ValidationContext {
            if (frozenRules == null || enabledRules == null) {
                throw new IllegalArgumentException("治理规则上下文不能为空");
            }
            validScopes = validScopes == null ? List.of() : List.copyOf(validScopes);
        }
    }
}
