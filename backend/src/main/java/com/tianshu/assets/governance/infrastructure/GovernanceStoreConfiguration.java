package com.tianshu.assets.governance.infrastructure;

import com.tianshu.assets.asset.domain.AssetRepository;
import com.tianshu.assets.governance.standard.application.GovernanceStandardImpactPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 治理侧仅剩的装配：把标准影响面端口接到资产仓储上。
 *
 * <p>真实数据库是唯一数据源，治理各端口（任务/问题/工作流/执行/规则/确认/验收/扫描/标准/映射）
 * 一律由 {@code JdbcGovernance*Store} 实现；这里不再提供任何内存兜底实现，
 * 库结构未就绪时应用直接启动失败，而不是静默退化成内存数据。
 */
@Configuration
public class GovernanceStoreConfiguration {

    @Bean
    @ConditionalOnMissingBean(GovernanceStandardImpactPort.class)
    GovernanceStandardImpactPort governanceStandardImpactPort(
            ObjectProvider<AssetRepository> assetRepositories) {
        return new RepositoryGovernanceStandardImpactAdapter(assetRepositories);
    }
}
