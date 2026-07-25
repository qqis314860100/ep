package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.governance.task.application.GovernanceEmployeeDirectory;
import com.tianshu.assets.governance.task.application.GovernanceTaskStore;
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
    @ConditionalOnMissingBean(GovernanceEmployeeDirectory.class)
    @ConditionalOnProperty(
            name = "asset.governance-schema-enabled",
            havingValue = "false",
            matchIfMissing = true)
    GovernanceEmployeeDirectory inMemoryGovernanceEmployeeDirectory() {
        return new InMemoryGovernanceEmployeeDirectory();
    }
}
