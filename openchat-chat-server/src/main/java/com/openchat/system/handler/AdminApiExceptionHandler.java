package com.openchat.system.handler;

import com.openchat.common.domain.vo.resp.ApiResult;
import com.openchat.common.exception.BusinessException;
import com.openchat.common.exception.CommonErrorEnum;
import com.openchat.system.controller.AdminApiController;
import com.openchat.system.exception.AdminApiException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** 管理 API 返回真实 HTTP 状态，错误响应不泄露配置、凭据或内部异常。 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AdminApiController.class)
public class AdminApiExceptionHandler {
    @ExceptionHandler(AdminApiException.class)
    public ResponseEntity<ApiResult<Void>> auth(AdminApiException exception, HttpServletResponse response) {
        if (exception.getStatus() == 401) response.setHeader("WWW-Authenticate", "Bearer");
        return error(exception.getStatus(), exception.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> business(BusinessException exception) {
        return error(400, exception.getErrorMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResult<Void>> tooLarge() {
        return error(413, "安装包超过服务器上传大小限制");
    }

    @ExceptionHandler({
            org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResult<Void>> invalid() {
        return error(400, "请求参数无效，请检查输入");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> unexpected(Exception exception) {
        log.error("管理接口执行失败，exceptionType={}", exception.getClass().getName());
        return error(500, "管理操作未完成，请刷新记录核对结果后再操作");
    }

    private ResponseEntity<ApiResult<Void>> error(int status, String message) {
        ApiResult<Void> result = ApiResult.fail(CommonErrorEnum.BUSINESS_ERROR, message);
        result.setCode(status);
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(result);
    }
}
