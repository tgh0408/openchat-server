package com.openchat.transaction.config;

import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** LiteTopic 客户端及其独立串行调度器，不依赖聊天业务。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LiteTopicPushProperties.class)
public class LiteTopicPushConfiguration {

    public static final String SUBSCRIPTION_EXECUTOR = "liteTopicSubscriptionExecutor";

    @Bean(name = SUBSCRIPTION_EXECUTOR, destroyMethod = "shutdownNow")
    public ScheduledExecutorService liteTopicSubscriptionExecutor() {
        // 保持原有单线程订阅顺序，不因迁移配置而改变并发语义。
        return Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("lite-topic-subscription-", 1).factory());
    }

    @Bean(destroyMethod = "close")
    public LiteTopicClient liteTopicClient(LiteTopicPushProperties properties,
            @Qualifier(SUBSCRIPTION_EXECUTOR) ScheduledExecutorService subscriptionExecutor) {
        return new LiteTopicClient(properties, ClientServiceProvider.loadService(), subscriptionExecutor);
    }

    @Bean
    @ConditionalOnMissingBean(MqConsumerShutdown.class)
    public MqConsumerShutdown mqConsumerShutdown(ConfigurableApplicationContext applicationContext) {
        return new MqConsumerShutdown(applicationContext);
    }
}
