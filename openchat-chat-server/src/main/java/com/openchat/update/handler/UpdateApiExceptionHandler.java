package com.openchat.update.handler;

import com.openchat.common.domain.vo.resp.ApiResult;
import com.openchat.common.exception.BusinessException;
import com.openchat.common.exception.CommonErrorEnum;
import com.openchat.update.controller.UpdateVersionController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 保留更新接口的错误协议，优先于聊天全局处理器且不影响其他业务。 */
@Slf4j
@Order(-1)
@RestControllerAdvice(assignableTypes = UpdateVersionController.class)
public class UpdateApiExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ApiResult<Void> business(BusinessException exception) {
        return ApiResult.fail(CommonErrorEnum.BUSINESS_ERROR, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResult<Void> invalid() {
        return ApiResult.fail(CommonErrorEnum.PARAM_INVALID, "请求参数无效");
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> unexpected(Exception exception) {
        log.error("更新查询失败，exceptionType={}", exception.getClass().getName());
        return ApiResult.fail(CommonErrorEnum.SYSTEM_ERROR, "查询更新失败，请稍后重试");
    }
}
