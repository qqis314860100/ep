package com.tianshu.assets.ai.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AiCapabilityClient 实现选择：
 * 默认（ai.capability.mock=false）使用 HttpAiCapabilityClient 指向真实能力服务；
 * 设 ai.capability.mock=true 时使用 FakeAiCapabilityClient（离线/排障/演示）。
 */
@Configuration
public class AiCapabilityClientConfiguration {

    @Bean
    @ConditionalOnProperty(name = "ai.capability.mock", havingValue = "true")
    FakeAiCapabilityClient fakeAiCapabilityClient() {
        return new FakeAiCapabilityClient();
    }

    @Bean
    @ConditionalOnProperty(name = "ai.capability.mock", havingValue = "false", matchIfMissing = true)
    HttpAiCapabilityClient httpAiCapabilityClient(AiCapabilityProperties properties) {
        return new HttpAiCapabilityClient(properties);
    }
}
