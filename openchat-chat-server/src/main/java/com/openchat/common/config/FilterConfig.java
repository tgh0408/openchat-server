package com.openchat.common.config;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.openchat.common.filter.RepeatableFilter;
import com.openchat.common.filter.RequestContextFilter;
import com.openchat.common.properties.AuthProperties;
import jakarta.servlet.DispatcherType;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Date;
import java.util.EnumSet;

@Configuration
public class FilterConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        // 全局日期格式转换配置
        registry.addConverter(String.class, Date.class, source -> {
            DateTime parse = DateUtil.parse(source);
            if (ObjectUtil.isNull(parse)) {
                return null;
            }
            return parse.toJdkDate();
        });
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
    }

    @Bean
    public FilterRegistrationBean<RequestContextFilter> requestContextFilterRegistration(AuthProperties authProperties) {
        FilterRegistrationBean<RequestContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestContextFilter(authProperties));
        registration.addUrlPatterns("/capi/*");
        // 避免与 Spring Boot 默认的 requestContextFilter 注册名冲突。
        registration.setName("openchatRequestContextFilter");
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);
        registration.setAsyncSupported(true);
        registration.setOrder(FilterRegistrationBean.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "openchat.plus-web-invoke-time-interceptor",
            name = "enable",
            havingValue = "true",
            matchIfMissing = false)
    public FilterRegistrationBean<RepeatableFilter> someFilterRegistration() {
        FilterRegistrationBean<RepeatableFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RepeatableFilter());
        registration.addUrlPatterns("/*");
        registration.setName("repeatableFilter");
        // MVC 拦截器也会处理错误页、转发和异步分派，每次分派都需要独立计时作用域。
        registration.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
        registration.setAsyncSupported(true);
        registration.setOrder(FilterRegistrationBean.HIGHEST_PRECEDENCE);
        return registration;
    }
}
