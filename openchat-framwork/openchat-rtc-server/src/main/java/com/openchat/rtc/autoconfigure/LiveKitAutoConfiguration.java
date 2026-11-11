package com.openchat.rtc.autoconfigure;

import com.openchat.rtc.service.LiveKitTokenService;
import io.livekit.server.AccessToken;
import io.livekit.server.WebhookReceiver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * LiveKit Server SDK 自动配置。
 */
@AutoConfiguration
@ConditionalOnClass(AccessToken.class)
@EnableConfigurationProperties(LiveKitProperties.class)
public class LiveKitAutoConfiguration {

    /** Webhook 与 Token 签发共用服务端密钥，SDK 同时校验 JWT 与正文摘要。 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "openchat.rtc.livekit", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public WebhookReceiver liveKitWebhookReceiver(LiveKitProperties properties) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()
                || properties.getApiSecret() == null || properties.getApiSecret().isBlank()) {
            throw new IllegalStateException("LiveKit 回调验签配置无效");
        }
        return new WebhookReceiver(properties.getApiKey(), properties.getApiSecret());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "openchat.rtc.livekit",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public LiveKitTokenService liveKitTokenService(LiveKitProperties properties) {
        return new LiveKitTokenService(properties);
    }
}
