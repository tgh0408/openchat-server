package com.openchat.transaction.mq;

import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.annotation.RocketMQMessageListenerBeanPostProcessor;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.core.annotation.AnnotationUtils;

/**
 * 将官方依赖初始化延后到监听器处理阶段，保留官方注解增强、容器注册与生命周期。
 * 此兼容补丁针对 RocketMQ Spring V5 2.3.6；升级依赖时需重新核对父类实现。
 */
public class DeferredRocketMqListenerBeanPostProcessor extends RocketMQMessageListenerBeanPostProcessor {

    private boolean initialized;

    @Override
    public void afterPropertiesSet() {
        // 此时仍在注册 BeanPostProcessor，不能取得容器配置或 AnnotationEnhancer 实例。
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
        if (AnnotationUtils.findAnnotation(targetClass, RocketMQMessageListener.class) != null
                || AnnotationUtils.findAnnotation(bean.getClass(), RocketMQMessageListener.class) != null) {
            initializeListenerDependencies();
        }
        return super.postProcessAfterInitialization(bean, beanName);
    }

    @Override
    public void start() {
        // 无监听器时也需初始化父类的容器配置，避免启动生命周期空指针。
        initializeListenerDependencies();
        super.start();
    }

    /** 仅协调本实例的初始化，不参与消息消费或集群业务互斥。 */
    private synchronized void initializeListenerDependencies() {
        if (initialized) {
            return;
        }
        try {
            super.afterPropertiesSet();
            initialized = true;
        } catch (Exception exception) {
            throw new BeanInitializationException("Failed to initialize RocketMQ listener dependencies", exception);
        }
    }
}
