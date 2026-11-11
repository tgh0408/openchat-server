package com.openchat.system.config;

import com.openchat.system.config.properties.AdminAuthProperties;
import com.openchat.system.intercepter.AdminWebInterceptor;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(AdminAuthProperties.class)
@RequiredArgsConstructor
public class AdminAuthConfiguration implements WebMvcConfigurer {
    private final AdminWebInterceptor interceptor;
    private final AdminAuthProperties properties;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        if (properties.isEnable()) {
            registry.addInterceptor(interceptor)
                    .addPathPatterns("/system/admin", "/system/admin/**")
                    .excludePathPatterns("/system/admin/login");
        }
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        if (properties.getAllowedOrigins().isEmpty()) return;
        for (String origin : properties.getAllowedOrigins()) {
            java.net.URI uri = java.net.URI.create(origin);
            boolean loopback = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || !uri.getPath().isEmpty()
                    || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && loopback))) {
                throw new IllegalArgumentException("Dashboard allowed-origins 必须是精确 HTTPS origin 或回环 HTTP origin");
            }
        }
        registry.addMapping("/system/admin/**")
                .allowedOrigins(properties.getAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type")
                .allowCredentials(false)
                .maxAge(600);
    }
}
