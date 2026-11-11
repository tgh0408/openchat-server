package com.openchat.common.exception;

import com.openchat.common.domain.vo.resp.ApiResult;
import com.openchat.common.utils.RequestHolder;
import com.openchat.rtc.domain.RtcAuditEvent;
import com.openchat.rtc.service.RtcCallConflictException;
import com.openchat.rtc.service.RtcOperationException;
import com.openchat.webrtc.service.RtcDeviceContext;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.MediaType;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.validation.UnexpectedTypeException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ApiResult<?> methodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.error(e.getMessage());
        int error_size = e.getBindingResult().getFieldErrors().size();
        if (error_size > 2) {
            //太多错误会导致前端展示数目过多
            FieldError error = e.getBindingResult().getFieldErrors().get(error_size - 1);
            return ApiResult.fail(CommonErrorEnum.PARAM_INVALID, error.getField() + " : " + error.getDefaultMessage());
        }
        StringBuilder stringBuilder = new StringBuilder();
        e.getBindingResult()
                .getFieldErrors()
                .forEach(mes ->
                        stringBuilder.append(mes.getField())
                                .append(",")
                                .append(mes.getDefaultMessage())
                                .append(",")
                );
        String errorMsg = stringBuilder.toString();
        return ApiResult.fail(CommonErrorEnum.PARAM_INVALID, errorMsg.substring(0, errorMsg.length() - 1));
    }

    /** 校验器配置错误属于服务端故障，不是用户参数错误。 */
    @ExceptionHandler(UnexpectedTypeException.class)
    public ApiResult<?> unexpectedTypeException(UnexpectedTypeException e) {
        log.error("参数校验器配置错误", e);
        return ApiResult.fail(CommonErrorEnum.SYSTEM_ERROR, CommonErrorEnum.SYSTEM_ERROR.getMsg());
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(value = BusinessException.class)
    public ApiResult<?> businessException(BusinessException e) {
        log.info("业务异常: {}", e.getMessage(), e);
        return ApiResult.fail(CommonErrorEnum.BUSINESS_ERROR, e.getErrorMessage());
    }

    @ExceptionHandler(value = NoHandlerFoundException.class)
    public ApiResult<?> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.error("未匹配到请求路径 : {} 错误信息 : {}", e.getStackTrace()[0].toString(), e.getMessage());
        Map<String, String> map = new HashMap<>();
        map.put("method", e.getHttpMethod());
        map.put("path", e.getRequestURL());
        return ApiResult.fail(
                CommonErrorEnum.NOT_FOUND_ERROR,
                CommonErrorEnum.NOT_FOUND_ERROR.getMsg() + "   " + e.getMessage(),
                map
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResult<?> illegalArgumentException(IllegalArgumentException e) {
        log.error("IllegalArgumentException: {}", e.getMessage());
        return ApiResult.fail(CommonErrorEnum.SYSTEM_ERROR, "illegalArgument");
    }

    /**
     * 异步长连接超时后响应已由容器结束，不能再写入统一 JSON 响应。
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void asyncRequestTimeout(AsyncRequestTimeoutException e) {
        log.debug("异步请求已超时，响应由容器关闭，异常信息: {}", e.getMessage(), e);
    }

    @ExceptionHandler(value = Throwable.class)
    public Object throwable(
            Throwable e,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (isSseRequest(request, response)) {
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            log.info(
                    "SSE 请求结束，跳过统一 JSON 异常响应，uri={}, committed={}, exceptionType={}, message={}",
                    request.getRequestURI(),
                    response.isCommitted(),
                    e.getClass().getName(),
                    e.getMessage());
            return null;
        }
        log.error("未处理的请求异常: {}", e.getMessage(), e);
        return ApiResult.fail(CommonErrorEnum.SYSTEM_ERROR, CommonErrorEnum.SYSTEM_ERROR.getMsg() + "   " + e.getMessage());
    }

    private boolean isSseRequest(
            HttpServletRequest request,
            HttpServletResponse response) {
        String responseContentType = response.getContentType();
        if (responseContentType != null
                && responseContentType.toLowerCase(Locale.ROOT)
                .startsWith(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return true;
        }
        Object producibleMediaTypes =
                request.getAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE);
        if (producibleMediaTypes instanceof Iterable<?> mediaTypes) {
            for (Object mediaType : mediaTypes) {
                if (MediaType.TEXT_EVENT_STREAM_VALUE.equalsIgnoreCase(String.valueOf(mediaType))) {
                    return true;
                }
            }
        }
        return request.getRequestURI().endsWith("/resource/sse");
    }

    @ExceptionHandler(RtcOperationException.class)
    public ApiResult<Void> operation(RtcOperationException exception) {
        log.info("event={} reason={} errorCode={}",
                RtcAuditEvent.RTC_OPERATION_REJECTED, exception.reason(), exception.reason().code());
        ApiResult<Void> result = ApiResult.fail(CommonErrorEnum.BUSINESS_ERROR, exception.getMessage());
        result.setErrCode(exception.reason().code());
        return result;
    }

    @ExceptionHandler({RtcCallConflictException.class, ConcurrencyFailureException.class})
    public ApiResult<Void> conflict(RuntimeException exception) {
        log.info("event={} exceptionType={}", RtcAuditEvent.RTC_STATE_CONFLICT, exception.getClass().getSimpleName());
        return operation(new RtcOperationException(RtcOperationException.Reason.STATE_CONFLICT));
    }

}
