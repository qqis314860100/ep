package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.governance.acceptance.application.GovernanceAcceptanceStore;
import com.tianshu.assets.governance.audit.application.GovernanceAuditStore;
import com.tianshu.assets.governance.acceptance.application.GovernanceAssetPort;
import com.tianshu.assets.governance.task.application.GovernanceEmployeeDirectory;
import com.tianshu.assets.governance.task.application.GovernanceTaskStore;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionStore;
import com.tianshu.assets.governance.confirmation.application.AssetResponsibilityPort;
import com.tianshu.assets.governance.confirmation.application.GovernanceConfirmationStore;
import com.tianshu.assets.governance.issue.application.GovernanceIssueStore;
import com.tianshu.assets.governance.task.application.GovernanceRuleCatalog;
import com.tianshu.assets.governance.task.application.GovernanceWorkflowStore;
import com.tianshu.assets.governance.standard.application.GovernanceDataStandardStore;
import com.tianshu.assets.governance.standard.application.GovernanceStandardImpactPort;
import com.tianshu.assets.governance.mapping.application.GovernanceMappingRuleStore;
import com.tianshu.assets.governance.scan.application.GovernanceScanRunStore;
import com.tianshu.assets.asset.domain.AssetRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GovernanceStoreConfiguration {
    @Bean
    @ConditionalOnMissingBean(GovernanceScanRunStore.class)
    @ConditionalOnProperty(name = "asset.governance-schema-enabled", havingValue = "false", matchIfMissing = true)
    GovernanceScanRunStore inMemoryGovernanceScanRunStore() {
        return new InMemoryGovernanceScanRunStore();
    }
    @Bean
    @ConditionalOnMissingBean(GovernanceMappingRuleStore.class)
    @ConditionalOnProperty(name = "asset.governance-schema-enabled", havingValue = "false", matchIfMissing = true)
    GovernanceMappingRuleStore inMemoryGovernanceMappingRuleStore() {
        return new InMemoryGovernanceMappingRuleStore();
    }
    @Bean
    @ConditionalOnMissingBean(GovernanceDataStandardStore.class)
    @ConditionalOnProperty(name = "asset.governance-schema-enabled", havingValue = "false", matchIfMissing = true)
    GovernanceDataStandardStore inMemoryGovernanceDataStandardStore() {
        return new InMemoryGovernanceDataStandardStore();
    }

    @Bean
    @ConditionalOnMissingBean(GovernanceStandardImpactPort.class)
    GovernanceStandardImpactPort governanceStandardImpactPort(
            ObjectProvider<AssetRepository> assetRepositories) {
        return new RepositoryGovernanceStandardImpactAdapter(assetRepositories);
    }

    @Bean
    @ConditionalOnMissingBean(GovernanceAuditStore.class)
    GovernanceAuditStore inMemoryGovernanceAuditStore() {
        return new InMemoryGovernanceAuditStore();
    }

    @Bean
    @ConditionalOnMissingBean(GovernanceTaskStore.class)
    @ConditionalOnProperty(
            name = "asset.governance-schema-enabled",
            havingValue = "false",
            matchIfMissing = true)
    GovernanceTaskStore inMemoryGovernanceTaskStore() {
        return InMemoryGovernanceTaskStore.withLegacySeed();
    }

    @Bean
    @ConditionalOnMissingBean(GovernanceIssueStore.class)
    @ConditionalOnProperty(
            name = "asset.governance-schema-enabled",
            havingValue = "false",
            matchIfMissing = true)
    GovernanceIssueStore inMemoryGovernanceIssueStore() {
        return InMemoryGovernanceIssueStore.withFieldSeeds();
    }

    @Bean
    @ConditionalOnMissingBean(GovernanceEmployeeDirectory.class)
    @ConditionalOnProperty(
            name = "asset.governance-schema-enabled",
            havingValue = "false",
            matchIfMissing = true)
    GovernanceEmployeeDirectory inMemoryGovernanceEmployeeDirectory() {
        return new InMemoryGovernanceEmployeeDirectory();
    }

    @Bean
    @ConditionalOnMissingBean(GovernanceWorkflowStore.class)
    @ConditionalOnProperty(
            name = "asset.governance-schema-enabled",
            havingValue = "false",
            matchIfMissing = true)
    GovernanceWorkflowStore inMemoryGovernanceWorkflowStore() {
        return new InMemoryGovernanceWorkflowStore();
    }

    @Bean
    @ConditionalOnMissingBean(GovernanceExecutionStore.class)
    @ConditionalOnProperty(
            name = "asset.governance-schema-enabled",
            havingValue = "false",
            matchIfMissing = true)
    GovernanceExecutionStore inMemoryGovernanceExecutionStore(GovernanceWorkflowStore workflowStore) {
        return new InMemoryGovernanceExecutionStore(workflowStore);
    }

    @Bean
    @ConditionalOnMissingBean(GovernanceRuleCatalog.class)
    @ConditionalOnProperty(
            name = "asset.governance-schema-enabled",
            havingValue = "false",
            matchIfMissing = true)
    GovernanceRuleCatalog inMemoryGovernanceRuleCatalog(GovernanceDataStandardStore standardStore) {
        return new InMemoryGovernanceRuleCatalog(standardStore);
    }

    @Bean
    @ConditionalOnMissingBean(GovernanceConfirmationStore.class)
    @ConditionalOnProperty(
            name = "asset.governance-schema-enabled",
            havingValue = "false",
            matchIfMissing = true)
    GovernanceConfirmationStore inMemoryGovernanceConfirmationStore() {
        return new InMemoryGovernanceConfirmationStore();
    }

    @Bean
    @ConditionalOnMissingBean(AssetResponsibilityPort.class)
    @ConditionalOnProperty(
            name = "asset.governance-schema-enabled",
            havingValue = "false",
            matchIfMissing = true)
    AssetResponsibilityPort inMemoryAssetResponsibilityAdapter() {
        var adapter = new InMemoryAssetResponsibilityAdapter();
        adapter.assign(101, "emp-li", "标准化小组");
        adapter.assign(102, "emp-li", "标准化小组");
        return adapter;
    }

    @Bean
    @ConditionalOnMissingBean(GovernanceAcceptanceStore.class)
    @ConditionalOnProperty(
            name = "asset.governance-schema-enabled",
            havingValue = "false",
            matchIfMissing = true)
    GovernanceAcceptanceStore inMemoryGovernanceAcceptanceStore() {
        return new InMemoryGovernanceAcceptanceStore();
    }

    @Bean
    @ConditionalOnMissingBean(GovernanceAssetPort.class)
    @ConditionalOnProperty(
            name = "asset.governance-schema-enabled",
            havingValue = "false",
            matchIfMissing = true)
    GovernanceAssetPort inMemoryGovernanceAssetPort() {
        return new InMemoryGovernanceAssetAdapter();
    }
}
