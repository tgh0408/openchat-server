package com.openchat.transaction.mq;

import org.apache.rocketmq.client.annotation.RocketMQMessageListenerBeanPostProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 修正 RocketMQ Spring V5 2.3.6 后置处理器提前创建容器配置的问题。 */
@Configuration(proxyBeanMethods = false)
public class RocketMqListenerInitializationConfiguration {

    /** 保留官方注册器的 Bean 名称，仅替换实现，避免重复注册消费者。 */
    @Bean
    static BeanFactoryPostProcessor rocketMqListenerInitializationPostProcessor() {
        return beanFactory -> {
            String beanName = RocketMQMessageListenerBeanPostProcessor.class.getName();
            if (!beanFactory.containsBeanDefinition(beanName)) {
                return;
            }
            BeanDefinition definition = beanFactory.getBeanDefinition(beanName);
            if (beanName.equals(definition.getBeanClassName())) {
                definition.setBeanClassName(DeferredRocketMqListenerBeanPostProcessor.class.getName());
            }
        };
    }
}
