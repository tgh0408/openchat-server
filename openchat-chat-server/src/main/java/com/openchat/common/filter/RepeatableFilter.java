package com.openchat.common.filter;

import com.openchat.common.context.WebInvokeTimeContext;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.Strings;
import org.springframework.http.MediaType;

import java.io.IOException;

public class RepeatableFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        // 重分派只绑定计时，不重读已消费的请求体，也不改变 include/异步响应的写出方式。
        if (request.getDispatcherType() != DispatcherType.REQUEST
                || !(request instanceof HttpServletRequest httpServletRequest)
                || !(response instanceof HttpServletResponse httpServletResponse)) {
            WebInvokeTimeContext.run(() -> chain.doFilter(request, response));
            return;
        }

        ServletRequest requestWrapper = null;
        if (Strings.CS.startsWith(request.getContentType(), MediaType.APPLICATION_JSON_VALUE)) {
            requestWrapper = new RepeatedlyRequestWrapper(httpServletRequest, response);
        }
        ServletRequest filteredRequest = requestWrapper == null ? request : requestWrapper;
        ResponseCaptureWrapper responseWrapper = new ResponseCaptureWrapper(httpServletResponse);
        Object previousCapture = httpServletRequest.getAttribute(ResponseCaptureWrapper.REQUEST_ATTRIBUTE);
        try {
            httpServletRequest.setAttribute(ResponseCaptureWrapper.REQUEST_ATTRIBUTE, responseWrapper);
            WebInvokeTimeContext.run(() -> chain.doFilter(filteredRequest, responseWrapper));
        } finally {
            if (previousCapture == null) {
                httpServletRequest.removeAttribute(ResponseCaptureWrapper.REQUEST_ATTRIBUTE);
            } else {
                httpServletRequest.setAttribute(ResponseCaptureWrapper.REQUEST_ATTRIBUTE, previousCapture);
            }
        }
    }

}
