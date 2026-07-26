package com.tianshu.assets.governance.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.tianshu.assets.governance.acceptance.application.GovernanceAcceptanceStore;
import com.tianshu.assets.governance.confirmation.application.AssetResponsibilityPort;
import com.tianshu.assets.governance.confirmation.application.GovernanceConfirmationStore;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionStore;
import com.tianshu.assets.governance.task.application.GovernanceEmployeeDirectory;
import com.tianshu.assets.governance.task.application.GovernanceTaskStore;
import com.tianshu.assets.governance.issue.application.GovernanceIssueStore;
import com.tianshu.assets.governance.task.application.GovernanceRuleCatalog;
import com.tianshu.assets.governance.task.application.GovernanceWorkflowStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GovernanceStoreConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GovernanceStoreConfiguration.class);

    @Test
    void suppliesOneFallbackForEachPortByDefault() {
        contextRunner.run(context -> {
            assertThat(context.getBeansOfType(GovernanceTaskStore.class)).hasSize(1);
            assertThat(context.getBeansOfType(GovernanceEmployeeDirectory.class)).hasSize(1);
            assertThat(context.getBeansOfType(GovernanceIssueStore.class)).hasSize(1);
            assertThat(context.getBeansOfType(GovernanceWorkflowStore.class)).hasSize(1);
            assertThat(context.getBeansOfType(GovernanceExecutionStore.class)).hasSize(1);
            assertThat(context.getBeansOfType(GovernanceRuleCatalog.class)).hasSize(1);
            assertThat(context.getBeansOfType(GovernanceConfirmationStore.class)).hasSize(1);
            assertThat(context.getBeansOfType(AssetResponsibilityPort.class)).hasSize(1);
            assertThat(context.getBeansOfType(GovernanceAcceptanceStore.class)).hasSize(1);
        });
    }

    @Test
    void customTaskStorePreventsOnlyTaskStoreFallback() {
        GovernanceTaskStore customStore = new InMemoryGovernanceTaskStore();

        contextRunner.withBean(GovernanceTaskStore.class, () -> customStore).run(context -> {
            assertThat(context.getBeansOfType(GovernanceTaskStore.class).values()).containsExactly(customStore);
            assertThat(context.getBeansOfType(GovernanceEmployeeDirectory.class)).hasSize(1);
        });
    }

    @Test
    void customEmployeeDirectoryPreventsOnlyDirectoryFallback() {
        GovernanceEmployeeDirectory customDirectory = List::of;

        contextRunner.withBean(GovernanceEmployeeDirectory.class, () -> customDirectory).run(context -> {
            assertThat(context.getBeansOfType(GovernanceEmployeeDirectory.class).values())
                    .containsExactly(customDirectory);
            assertThat(context.getBeansOfType(GovernanceTaskStore.class)).hasSize(1);
        });
    }

    @Test
    void customIssueStorePreventsOnlyIssueStoreFallback() {
        GovernanceIssueStore customStore = new InMemoryGovernanceIssueStore();

        contextRunner.withBean(GovernanceIssueStore.class, () -> customStore).run(context -> {
            assertThat(context.getBeansOfType(GovernanceIssueStore.class).values()).containsExactly(customStore);
            assertThat(context.getBeansOfType(GovernanceTaskStore.class)).hasSize(1);
        });
    }

    @Test
    void schemaFlagDisablesAllInMemoryGovernanceStores() {
        contextRunner.withPropertyValues("asset.governance-schema-enabled=true").run(context -> {
            assertThat(context.getBeansOfType(GovernanceIssueStore.class)).isEmpty();
            assertThat(context.getBeansOfType(GovernanceTaskStore.class)).isEmpty();
            assertThat(context.getBeansOfType(GovernanceEmployeeDirectory.class)).isEmpty();
            assertThat(context.getBeansOfType(GovernanceWorkflowStore.class)).isEmpty();
            assertThat(context.getBeansOfType(GovernanceExecutionStore.class)).isEmpty();
            assertThat(context.getBeansOfType(GovernanceRuleCatalog.class)).isEmpty();
            assertThat(context.getBeansOfType(GovernanceConfirmationStore.class)).isEmpty();
            assertThat(context.getBeansOfType(AssetResponsibilityPort.class)).isEmpty();
            assertThat(context.getBeansOfType(GovernanceAcceptanceStore.class)).isEmpty();
        });
    }
}
