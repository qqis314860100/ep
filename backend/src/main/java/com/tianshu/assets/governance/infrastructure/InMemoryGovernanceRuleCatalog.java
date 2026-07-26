package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.asset.domain.AssetScope;
import com.tianshu.assets.governance.task.application.GovernanceRuleCatalog;
import com.tianshu.assets.governance.task.domain.GovernanceRuleSnapshot;
import java.util.List;
import java.util.Map;

public class InMemoryGovernanceRuleCatalog implements GovernanceRuleCatalog {

    private final GovernanceRuleSnapshot enabledSnapshot;
    private final List<AssetScope> validScopes;

    public InMemoryGovernanceRuleCatalog() {
        this(new GovernanceRuleSnapshot(
                0, "FIELD-COMPLETENESS", 1, 1,
                Map.of("specialty", 5L, "scope", 8L), "FIELD-QUALITY", 2));
    }

    public InMemoryGovernanceRuleCatalog(GovernanceRuleSnapshot enabledSnapshot) {
        this(enabledSnapshot, List.of(
                new AssetScope(
                        "乘用车", "H03", "宁德基地", "A 拉线", "焊接段", "乘用车", "底部水冷"),
                new AssetScope(
                        "乘用车", "H03", "宁德基地", "A 拉线", "焊接段", "乘用车", "大面水冷"),
                new AssetScope(
                        "乘用车", "H03", "溧阳基地", "B 拉线", "焊接段", "乘用车", "大面水冷"),
                new AssetScope(
                        "商用车", "P02", "溧阳基地", "B 拉线", "PACK 段", "商用车", "商用车")));
    }

    public InMemoryGovernanceRuleCatalog(
            GovernanceRuleSnapshot enabledSnapshot, List<AssetScope> validScopes) {
        this.enabledSnapshot = enabledSnapshot;
        this.validScopes = List.copyOf(validScopes);
    }

    @Override
    public GovernanceRuleSnapshot enabledSnapshot() {
        return new GovernanceRuleSnapshot(
                enabledSnapshot.id(), enabledSnapshot.dataStandardId(), enabledSnapshot.dataStandardVersion(),
                enabledSnapshot.fieldRuleVersion(), enabledSnapshot.dictionaryVersions(),
                enabledSnapshot.qualityPolicyId(), enabledSnapshot.qualityPolicyVersion());
    }

    @Override
    public List<AssetScope> validScopes() {
        return validScopes;
    }
}
