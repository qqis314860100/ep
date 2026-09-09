package com.tianshu.assets.ai.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AiCapabilityClient 实现选择：
 * 默认（ai.capability.mock=true 或缺省）使用 FakeAiCapabilityClient（dev/单测/联调）；
 * 设 ai.capability.mock=false 时使用 HttpAiCapabilityClient 指向真实能力服务（真链路冒烟/联调）。
 */
@Configuration
public class AiCapabilityClientConfiguration {

    @Bean
    @ConditionalOnProperty(name = "ai.capability.mock", havingValue = "true", matchIfMissing = true)
    FakeAiCapabilityClient fakeAiCapabilityClient() {
        return new FakeAiCapabilityClient();
    }

    @Bean
    @ConditionalOnProperty(name = "ai.capability.mock", havingValue = "false")
    HttpAiCapabilityClient httpAiCapabilityClient(AiCapabilityProperties properties) {
        return new HttpAiCapabilityClient(properties);
    }
}
