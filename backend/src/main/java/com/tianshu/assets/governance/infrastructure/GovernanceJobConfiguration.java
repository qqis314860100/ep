package com.tianshu.assets.governance.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class GovernanceJobConfiguration {

    @Bean(name = "governanceJobExecutor")
    ThreadPoolTaskExecutor governanceJobExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("governance-job-");
        executor.initialize();
        return executor;
    }
}
