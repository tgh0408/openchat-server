package com.openchat.common.exception;

import cn.hutool.http.ContentType;
import com.openchat.common.domain.vo.resp.ApiResult;
import com.openchat.common.utils.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@AllArgsConstructor
@Getter
public enum CommonErrorEnum implements ErrorEnum{
    SYSTEM_ERROR(-1,"系统出现未知错误",500),
    PARAM_INVALID(-2,"参数校验失败",503),
    BUSINESS_ERROR(-3,"{0}",400),
    LOCK_ERROR(-3,"操作太频繁了~",6000),
    NOT_FOUND_ERROR(-4,"未找到资源路径",404),
    ACCESS_DENIED(-5,"登录失效请重新登录",403),
    ID_BLACK(-6,"您的ID已被拉黑,禁止访问",401),
    IP_BLACK(-7,"您的IP已被拉黑,禁止访问",401);
    private final Integer code;
    private final String msg;
    private final Integer statusCode;

    @Override
    public Integer getErrorCode() {
        return this.code;
    }

    @Override
    public String getErrorMsg() {
        return this.msg;
    }

    public void sendHttpError(HttpServletResponse response) throws IOException {
        response.setStatus(statusCode);
        response.setContentType(ContentType.JSON.toString(StandardCharsets.UTF_8));
        response.getWriter().write(JsonUtils.toStr(ApiResult.fail(this,msg)));
    }
}
