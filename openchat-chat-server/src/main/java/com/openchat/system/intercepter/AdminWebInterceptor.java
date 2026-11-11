package com.openchat.system.intercepter;

import com.openchat.system.domain.dto.AdminPrincipal;
import com.openchat.system.exception.AdminApiException;
import com.openchat.system.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AdminWebInterceptor implements HandlerInterceptor {
    private static final String PRINCIPAL_ATTRIBUTE = AdminPrincipal.class.getName();
    private final AdminAuthService authService;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, HttpServletResponse response, @NonNull Object handler) {
        response.setHeader("Cache-Control", "no-store");
        if (CorsUtils.isPreFlightRequest(request)) return true;
        var headers = request.getHeaders("Authorization");
        String authorization = headers.hasMoreElements() ? headers.nextElement() : null;
        if (headers.hasMoreElements() || authorization == null
                || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                || authorization.indexOf(',') >= 0) {
            throw new AdminApiException(401, "请先登录管理后台");
        }
        request.setAttribute(PRINCIPAL_ATTRIBUTE, authService.authenticate(authorization.substring(7)));
        return true;
    }

    /** 控制器自身也要求已验证身份，避免漏注册拦截器时开放管理操作。 */
    public static AdminPrincipal requirePrincipal(HttpServletRequest request) {
        if (request.getAttribute(PRINCIPAL_ATTRIBUTE) instanceof AdminPrincipal principal) {
            return principal;
        }
        throw new AdminApiException(401, "请先登录管理后台");
    }
}
