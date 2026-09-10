package com.tianshu.assets.governance.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.tianshu.assets.governance.acceptance.application.GovernanceAcceptanceStore;
import com.tianshu.assets.governance.confirmation.application.AssetResponsibilityPort;
import com.tianshu.assets.governance.confirmation.application.GovernanceConfirmationStore;
import com.tianshu.assets.governance.execution.application.GovernanceExecutionStore;
import com.tianshu.assets.governance.issue.application.GovernanceIssueStore;
import com.tianshu.assets.governance.mapping.application.GovernanceMappingRuleStore;
import com.tianshu.assets.governance.scan.application.GovernanceScanRunStore;
import com.tianshu.assets.governance.standard.application.GovernanceDataStandardStore;
import com.tianshu.assets.governance.standard.application.GovernanceStandardImpactPort;
import com.tianshu.assets.governance.task.application.GovernanceEmployeeDirectory;
import com.tianshu.assets.governance.task.application.GovernanceRuleCatalog;
import com.tianshu.assets.governance.task.application.GovernanceTaskStore;
import com.tianshu.assets.governance.task.application.GovernanceWorkflowStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 回归护栏：真实数据库是唯一数据源，装配层不得再提供任何内存兜底的数据端口。
 *
 * <p>{@link GovernanceStoreConfiguration} 只负责把标准影响面端口接到资产仓储上；治理各数据端口
 * 一律由 {@code JdbcGovernance*Store} 提供，库结构未就绪时应直接启动失败，而不是静默退回内存种子数据。
 */
class GovernanceStoreConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GovernanceStoreConfiguration.class);

    @Test
    void suppliesOnlyTheStandardImpactPortAndNoDataStoreFallback() {
        contextRunner.run(context -> {
            assertThat(context.getBeansOfType(GovernanceStandardImpactPort.class)).hasSize(1);
            assertThat(context.getBeansOfType(GovernanceTaskStore.class)).isEmpty();
            assertThat(context.getBeansOfType(GovernanceEmployeeDirectory.class)).isEmpty();
            assertThat(context.getBeansOfType(GovernanceIssueStore.class)).isEmpty();
            assertThat(context.getBeansOfType(GovernanceWorkflowStore.class)).isEmpty();
            assertThat(context.getBeansOfType(GovernanceExecutionStore.class)).isEmpty();
            assertThat(context.getBeansOfType(GovernanceRuleCatalog.class)).isEmpty();
            assertThat(context.getBeansOfType(GovernanceConfirmationStore.class)).isEmpty();
            assertThat(context.getBeansOfType(AssetResponsibilityPort.class)).isEmpty();
            assertThat(context.getBeansOfType(GovernanceAcceptanceStore.class)).isEmpty();
            assertThat(context.getBeansOfType(GovernanceDataStandardStore.class)).isEmpty();
            assertThat(context.getBeansOfType(GovernanceScanRunStore.class)).isEmpty();
            assertThat(context.getBeansOfType(GovernanceMappingRuleStore.class)).isEmpty();
        });
    }

    @Test
    void keepsTheStandardImpactPortWhenSchemaFlagIsOn() {
        contextRunner.withPropertyValues("asset.governance-schema-enabled=true").run(context ->
                assertThat(context.getBeansOfType(GovernanceStandardImpactPort.class)).hasSize(1));
    }
}
