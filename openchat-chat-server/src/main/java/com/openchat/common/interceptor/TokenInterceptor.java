package com.openchat.common.interceptor;

import com.openchat.common.domain.dto.RequestInfo;
import com.openchat.common.exception.CommonErrorEnum;
import com.openchat.common.utils.RequestHolder;
import com.openchat.user.service.LoginService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.cors.CorsUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TokenInterceptor implements HandlerInterceptor {
    public static final String AUTHORIZATION_SCHEMA = "Bearer ";
    private static final String Authorization = "Authorization";
    final LoginService loginService;


    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        if (CorsUtils.isPreFlightRequest(request)) return true;
        RequestInfo requestInfo = RequestHolder.get();
        // 上下文必须由入口过滤器建立，缺失时不能绕过公开接口的标识校验。
        if (requestInfo == null) {
            CommonErrorEnum.ACCESS_DENIED.sendHttpError(response);
            return false;
        }
        String token = getToken(request);
        Long uid = loginService.getValidUid(token, requestInfo.getDeviceId(), requestInfo.getTerminal());
        //能解析出这个token
        if (Objects.isNull(uid)) {
            CommonErrorEnum.ACCESS_DENIED.sendHttpError(response);
            //返回401
            return false;
        }
        requestInfo.setUid(uid);
        requestInfo.setToken(token);
        return true;
    }

    private String getToken(HttpServletRequest request) {
        String header = request.getHeader(Authorization);
        return Optional.ofNullable(header)
                .filter(h -> h.startsWith(AUTHORIZATION_SCHEMA))
                .map(h -> h.replace(AUTHORIZATION_SCHEMA, ""))
                .orElse(null);
    }

}
