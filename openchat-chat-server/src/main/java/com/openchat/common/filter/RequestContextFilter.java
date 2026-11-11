package com.openchat.common.filter;

import com.openchat.common.domain.dto.RequestInfo;
import com.openchat.common.exception.CommonErrorEnum;
import com.openchat.common.properties.AuthProperties;
import com.openchat.common.utils.JwtUtils;
import com.openchat.common.utils.RequestHolder;
import com.openchat.common.utils.StringUtils;
import com.openchat.user.domain.enums.TerminalEnum;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 统一校验客户端标识，并管理每次请求分派的线程上下文。 */
@RequiredArgsConstructor
public class RequestContextFilter extends OncePerRequestFilter {
    public static final String DEVICE_ID_HEADER = "X-Device-Id";
    public static final String CLIENT_ID_HEADER = "clientId";
    private final AuthProperties authProperties;

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        RequestHolder.remove();
        try {
            // 预检没有实际客户端标识值，仍交给原有 CORS 策略处理。
            if (CorsUtils.isPreFlightRequest(request)) {
                chain.doFilter(request, response);
                return;
            }
            TerminalEnum terminal = authProperties.resolveTerminal(getSingleHeader(request, CLIENT_ID_HEADER));
            String deviceId = getSingleHeader(request, DEVICE_ID_HEADER);
            if (terminal == null || !JwtUtils.isValidDeviceId(deviceId)) {
                CommonErrorEnum.ACCESS_DENIED.sendHttpError(response);
                return;
            }
            RequestInfo requestInfo = new RequestInfo();
            requestInfo.setTerminal(terminal);
            requestInfo.setDeviceId(deviceId);
            requestInfo.setIp(getClientIp(request));
            RequestHolder.set(requestInfo);
            chain.doFilter(request, response);
        } finally {
            // 包括拒绝、异常和 SSE 启动异步处理后的线程归还，均不得残留身份。
            RequestHolder.remove();
        }
    }

    /** 拒绝重复或合并的标识头，避免代理与应用采用不同的值。 */
    private String getSingleHeader(HttpServletRequest request, String name) {
        var headers = request.getHeaders(name);
        if (headers == null || !headers.hasMoreElements()) return null;
        String value = headers.nextElement();
        return headers.hasMoreElements() || value == null || value.contains(",") ? null : value;
    }

    private String getClientIp(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR"
        };
        for (String headerName : headers) {
            String value = request.getHeader(headerName);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String[] whitePaths = authProperties.getWhitePath();
        if (whitePaths == null) return false;
        // 白名单是应用内路径，与 MVC 拦截器保持一致，不包含部署的 contextPath。
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (String pattern : whitePaths) {
            if (StringUtils.isMatch(pattern, path)) return true;
        }
        return false;
    }
}
