package com.openchat.common.interceptor;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import com.openchat.common.context.WebInvokeTimeContext;
import com.openchat.common.filter.RepeatedlyRequestWrapper;
import com.openchat.common.filter.ResponseCaptureWrapper;
import com.openchat.common.properties.PlusWebInvokeTimeProperties;
import com.openchat.common.utils.JsonUtils;
import com.openchat.common.utils.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.time.StopWatch;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * web的调用时间统计拦截器
 */
@Slf4j
public class PlusWebInvokeTimeInterceptor implements HandlerInterceptor {

    private static final String REQUEST_ID_ATTRIBUTE =
            PlusWebInvokeTimeInterceptor.class.getName() + ".REQUEST_ID";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    // 排除数组
    private final String[] EXCLUDE_PROPERTIES;
    private final String[] RESPONSE_EXCLUDE_PROPERTIES;
    private final boolean printHeader;
    private final String[] allowPath;

    public PlusWebInvokeTimeInterceptor(PlusWebInvokeTimeProperties properties){
        this.EXCLUDE_PROPERTIES = properties.getExcludeProperties();
        this.RESPONSE_EXCLUDE_PROPERTIES = properties.getResponseExcludeProperties();
        this.printHeader = properties.isPrintHeader();
        this.allowPath = properties.getAllowedPath();
    }

    @Override
    @NullMarked
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!isAllowedPath(request.getRequestURI())) {
            return true;
        }
        String requestId = getOrCreateRequestId(request);
        String url = request.getMethod() + " " + request.getRequestURI();
        response.setHeader(REQUEST_ID_HEADER, requestId);
        // 打印请求参数
        if (isJsonRequest(request)) {
            String jsonParam = "";
            RepeatedlyRequestWrapper requestWrapper = findRepeatedlyRequestWrapper(request);
            if (requestWrapper != null) {
                jsonParam = IoUtil.read(requestWrapper.getReader());
                if (StringUtils.isNotBlank(jsonParam)) {
                    JsonMapper mapper = JsonUtils.getJsonMapper();
                    JsonNode rootNode = mapper.readTree(jsonParam);
                    removeSensitiveFields(rootNode, EXCLUDE_PROPERTIES);
                    jsonParam = rootNode.toString();
                }
            }
            log.info("[PLUS][requestId={}]开始请求 => URL[{}],参数类型[json],参数:[{}]", requestId, url, jsonParam);
        } else {
            Map<String, String[]> parameterMap = request.getParameterMap();
            if (MapUtil.isNotEmpty(parameterMap)) {
                Map<String, String[]> map = new LinkedHashMap<>(parameterMap);
                MapUtil.removeAny(map, EXCLUDE_PROPERTIES);
                String parameters = JsonUtils.toStr(map);
                log.info("[PLUS][requestId={}]开始请求 => URL[{}],参数类型[param],参数:[{}]", requestId, url, parameters);
            } else {
                log.info("[PLUS][requestId={}]开始请求 => URL[{}],无参数", requestId, url);
            }
        }

        if (printHeader) {
            log.info("[PLUS][requestId={}]request headers:[{}]",
                    requestId, JsonUtils.toStr(getRequestHeaders(request)));
        }

        StopWatch stopWatch = WebInvokeTimeContext.getStopWatch();
        if (ObjectUtil.isNull(stopWatch)) {
            return true;
        }
        stopWatch.start();

        return true;
    }

    /**
     * 空列表不限制日志；配置路径时复用 Ant 风格匹配，支持通配符和路径变量。
     */
    private boolean isAllowedPath(String requestUri) {
        if (ArrayUtil.isEmpty(allowPath)) {
            return true;
        }
        for (String pattern : allowPath) {
            if (StringUtils.isMatch(pattern, requestUri)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, List<String>> getRequestHeaders(HttpServletRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        if (request.getHeaderNames() == null) {
            return headers;
        }

        for (String headerName : Collections.list(request.getHeaderNames())) {
            List<String> values = Collections.list(request.getHeaders(headerName));
            headers.put(headerName, values);
        }
        return headers;
    }

    private String getOrCreateRequestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (requestId instanceof String existingRequestId && StringUtils.isNotBlank(existingRequestId)) {
            return existingRequestId;
        }
        String newRequestId = UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, newRequestId);
        return newRequestId;
    }

    /**
     * 沿请求包装器链查找可重复读取请求体的包装器。
     *
     * @param request 当前请求
     * @return 可重复读取包装器，未找到时返回 null
     */
    private RepeatedlyRequestWrapper findRepeatedlyRequestWrapper(HttpServletRequest request) {
        HttpServletRequest current = request;
        while (true) {
            if (current instanceof RepeatedlyRequestWrapper requestWrapper) {
                return requestWrapper;
            }
            if (!(current instanceof HttpServletRequestWrapper requestWrapper)) {
                return null;
            }
            if (!(requestWrapper.getRequest() instanceof HttpServletRequest nestedRequest)) {
                return null;
            }
            current = nestedRequest;
        }
    }

    private void removeSensitiveFields(JsonNode node, String[] excludeProperties) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            List<String> fieldsToRemove = new ArrayList<>();

            // 直接使用增强 for 循环遍历属性
            // tools.jackson 的 properties() 通常返回 Iterable<Map.Entry<String, JsonNode>>
            for (Map.Entry<String, JsonNode> entry : objectNode.properties()) {
                String fieldName = entry.getKey();
                if (ArrayUtil.contains(excludeProperties, fieldName)) {
                    fieldsToRemove.add(fieldName);
                }
            }
            // 执行删除
            for (String field : fieldsToRemove) {
                objectNode.remove(field);
            }
            // 递归处理删除后剩下的子节点
            for (Map.Entry<String, JsonNode> entry : objectNode.properties()) {
                removeSensitiveFields(entry.getValue(), excludeProperties);
            }
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (JsonNode child : arrayNode) {
                removeSensitiveFields(child, excludeProperties);
            }
        }
    }

    @Override
    public void postHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, ModelAndView modelAndView) {

    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) {
        if (!isAllowedPath(request.getRequestURI())) {
            return;
        }
        StopWatch stopWatch = WebInvokeTimeContext.getStopWatch();
        if (ObjectUtil.isNotNull(stopWatch)) {
            stopWatch.stop();
            String requestId = getOrCreateRequestId(request);
            log.info("[PLUS][requestId={}]响应 JSON => [{}]", requestId, getResponseJson(request, response));
            log.info("[PLUS][requestId={}]结束请求 => URL[{}],耗时:[{}]毫秒",
                    requestId,
                    request.getMethod() + " " + request.getRequestURI(),
                    stopWatch.getDuration().toMillis());
        }
    }

    /**
     * 判断本次请求的数据类型是否为json
     *
     * @param request request
     * @return boolean
     */
    private boolean isJsonRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType != null) {
            return Strings.CI.startsWith(contentType, MediaType.APPLICATION_JSON_VALUE);
        }
        return false;
    }

    private String getResponseJson(HttpServletRequest request, HttpServletResponse response) {
        ResponseCaptureWrapper responseWrapper = findResponseCaptureWrapper(request, response);
        if (responseWrapper == null) {
            return "";
        }

        byte[] body = responseWrapper.getCapturedBody();
        if (body.length == 0) {
            return "";
        }

        Charset charset = getResponseCharset(response);
        String responseJson = new String(body, charset);
        try {
            JsonMapper mapper = JsonUtils.getJsonMapper();
            JsonNode rootNode = mapper.readTree(responseJson);
            if (rootNode == null) {
                return "";
            }
            removeSensitiveFields(rootNode, RESPONSE_EXCLUDE_PROPERTIES);
            return rootNode.toString();
        } catch (RuntimeException exception) {
            if (isJsonResponse(response)) {
                log.warn("[PLUS]响应不是有效 JSON", exception);
            }
            return "";
        }
    }

    private Charset getResponseCharset(HttpServletResponse response) {
        String contentType = response.getContentType();
        if (StringUtils.isNotBlank(contentType)) {
            try {
                Charset charset = MediaType.parseMediaType(contentType).getCharset();
                if (charset != null) {
                    return charset;
                }
            } catch (IllegalArgumentException ignored) {
                // 响应头格式异常时继续使用 JSON 的默认编码。
            }
        }

        if (isJsonResponse(response)) {
            return StandardCharsets.UTF_8;
        }

        String encoding = response.getCharacterEncoding();
        if (StringUtils.isBlank(encoding)) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (IllegalArgumentException ignored) {
            return StandardCharsets.UTF_8;
        }
    }

    private ResponseCaptureWrapper findResponseCaptureWrapper(
            HttpServletRequest request, HttpServletResponse response) {
        Object requestAttribute = request.getAttribute(ResponseCaptureWrapper.REQUEST_ATTRIBUTE);
        if (requestAttribute instanceof ResponseCaptureWrapper responseWrapper) {
            return responseWrapper;
        }
        if (response instanceof ResponseCaptureWrapper responseWrapper) {
            return responseWrapper;
        }
        return null;
    }

    private boolean isJsonResponse(HttpServletResponse response) {
        String contentType = response.getContentType();
        return contentType != null
                && Strings.CI.startsWith(contentType, MediaType.APPLICATION_JSON_VALUE);
    }

}
