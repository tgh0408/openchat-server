package com.openchat.common.configuration;

import com.openchat.common.interceptor.PlusWebInvokeTimeInterceptor;
import com.openchat.common.properties.PlusWebInvokeTimeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PlusWebInvokeTimeProperties.class)
@ConditionalOnProperty(value = "openchat.plus-web-invoke-time-interceptor.enable", havingValue = "true")
public class PlusWebInvokeTimeConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "openchat.plus-web-invoke-time-interceptor",
            name = "enable",
            havingValue = "true",
            matchIfMissing = false)
    public PlusWebInvokeTimeInterceptor PlusWebInvokeTimeInterceptor(PlusWebInvokeTimeProperties plusWebInvokeTimeProperties) {
        return new PlusWebInvokeTimeInterceptor(plusWebInvokeTimeProperties);
    }
}
