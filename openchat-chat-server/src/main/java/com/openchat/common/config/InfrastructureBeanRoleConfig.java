package com.openchat.common.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 标记启动阶段会被 BeanPostProcessor 提前创建、但不需要业务代理的基础设施 Bean。
 */
@Configuration(proxyBeanMethods = false)
public class InfrastructureBeanRoleConfig {

    /**
     * 在 BeanPostProcessor 注册前设置基础设施角色，避免 Spring 对正常初始化顺序的误报。
     *
     * @return BeanFactoryPostProcessor
     */
    @Bean
    static BeanFactoryPostProcessor infrastructureBeanRolePostProcessor() {
        return beanFactory -> {
            for (String beanName : beanFactory.getBeanDefinitionNames()) {
                BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
                if (isInfrastructureBean(beanName, beanDefinition)) {
                    beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
                }
            }
        };
    }

    private static boolean isInfrastructureBean(String beanName, BeanDefinition beanDefinition) {
        if ("cacheConfig".equals(beanName)) {
            return true;
        }
        return isSpringCloudCommonsType(beanDefinition.getBeanClassName())
                || isSpringCloudCommonsType(beanDefinition.getFactoryBeanName())
                || "org.springframework.cloud.client.loadbalancer.LoadBalancerDefaultMappingsProviderAutoConfiguration"
                .equals(beanDefinition.getBeanClassName())
                || "org.springframework.cloud.client.loadbalancer.LoadBalancerDefaultMappingsProviderAutoConfiguration"
                .equals(beanDefinition.getFactoryBeanName());
    }

    private static boolean isSpringCloudCommonsType(String typeName) {
        return typeName != null && typeName.startsWith("org.springframework.cloud.commons.config.");
    }
}
