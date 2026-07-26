package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.governance.task.application.GovernanceEmployeeDirectory;
import com.tianshu.assets.governance.task.application.GovernanceTaskStore;
import com.tianshu.assets.governance.issue.application.GovernanceIssueStore;
import com.tianshu.assets.governance.task.application.GovernanceRuleCatalog;
import com.tianshu.assets.governance.task.application.GovernanceWorkflowStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GovernanceStoreConfiguration {

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
    @ConditionalOnMissingBean(GovernanceRuleCatalog.class)
    @ConditionalOnProperty(
            name = "asset.governance-schema-enabled",
            havingValue = "false",
            matchIfMissing = true)
    GovernanceRuleCatalog inMemoryGovernanceRuleCatalog() {
        return new InMemoryGovernanceRuleCatalog();
    }
}
