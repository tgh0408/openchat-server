package com.openchat.common.config;

import com.openchat.common.interceptor.BlackInterceptor;
import com.openchat.common.interceptor.PlusWebInvokeTimeInterceptor;
import com.openchat.common.interceptor.TokenInterceptor;
import com.openchat.common.properties.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
@Order(1)
@RequiredArgsConstructor
public class InterceptorConfig implements WebMvcConfigurer {
    final TokenInterceptor tokenInterceptor;
    final BlackInterceptor blackInterceptor;
    final ObjectProvider<PlusWebInvokeTimeInterceptor> plusWebInvokeTimeInterceptor;
    final AuthProperties authProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/capi/**")
                // 服务端回调仅由 LiveKit SDK 验签，不接受用户登录凭据代替签名。
                .excludePathPatterns("/capi/*/public/**")
                .excludePathPatterns(authProperties.getWhitePath());
        registry.addInterceptor(blackInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(authProperties.getWhitePath());
        // 管理接口和 LiveKit 回调包含凭据，不能进入输出原始头及正文的调试日志。
        plusWebInvokeTimeInterceptor.ifAvailable(interceptor ->
                        registry.addInterceptor(interceptor)
                .excludePathPatterns("/system/admin", "/system/admin/**")
                .excludePathPatterns(authProperties.getWhitePath())
        );
    }
}
