package com.openchat.common.sse.config;

import com.openchat.common.config.ThreadPoolConfig;
import com.openchat.common.sse.core.SseEmitterManager;
import com.openchat.transaction.service.MQProducer;
import com.openchat.user.service.LoginSessionStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * SSE 自动装配
 *
 * @author Lion Li
 */
@Configuration
@ConditionalOnProperty(value = "sse.enabled", havingValue = "true")
@EnableConfigurationProperties(SseProperties.class)
public class SseAutoConfiguration {

    @Bean
    public SseEmitterManager sseEmitterManager(MQProducer mqProducer, SseProperties properties,
            ApplicationEventPublisher applicationEventPublisher,
            @Qualifier(ThreadPoolConfig.SSE_PUSH_EXECUTOR) ThreadPoolExecutor pushExecutor,
            @Qualifier(ThreadPoolConfig.SSE_HEARTBEAT_EXECUTOR) ScheduledExecutorService heartbeatExecutor,
            LoginSessionStore sessions) {
        return new SseEmitterManager(mqProducer, properties, applicationEventPublisher,
                pushExecutor, heartbeatExecutor, sessions);
    }

}
