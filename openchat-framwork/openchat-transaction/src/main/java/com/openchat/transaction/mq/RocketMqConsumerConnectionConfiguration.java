package com.openchat.transaction.mq;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.autoconfigure.RocketMQProperties;
import org.apache.rocketmq.client.support.DefaultListenerContainer;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 官方注解的 boolean/int 属性不支持占位符，容器初始化前沿用公共连接参数。 */
@Component
@RequiredArgsConstructor
public class RocketMqConsumerConnectionConfiguration implements BeanPostProcessor {

    private final ObjectProvider<RocketMQProperties> properties;

    @Override
    public Object postProcessBeforeInitialization(@NonNull Object bean, @NonNull String beanName) {
        if (bean instanceof DefaultListenerContainer container) {
            RocketMQProperties.Producer connection = properties.getObject().getProducer();
            container.setSslEnabled(connection.isSslEnabled());
            container.setRequestTimeout(Duration.ofSeconds(connection.getRequestTimeout()));
        }
        return bean;
    }
}
