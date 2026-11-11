package com.openchat.common.domain.vo.resp;

import com.openchat.common.domain.enums.RespStatusCode;
import com.openchat.common.exception.CommonErrorEnum;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Description: 通用返回体
 * Author:
 * Date: 2023-03-23
 */
@Data
@Schema(description = "基础返回体")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiResult<T> {
    @Schema(description = "成功标识true or false")
    private Boolean success;
    @Schema(description = "成功标识code")
    private Integer code;
    @Schema(description = "错误码")
    private Integer errCode;
    @Schema(description = "错误消息")
    private String errMsg;
    @Schema(description = "返回对象")
    private T data;

    public static <T> ApiResult<T> success() {
        ApiResult<T> result = new ApiResult<T>();
        result.setData(null);
        result.setCode(RespStatusCode.SUCCESS.getStatus());
        result.setSuccess(Boolean.TRUE);
        return result;
    }

    public static <T> ApiResult<T> success(T data) {
        ApiResult<T> result = new ApiResult<T>();
        result.setData(data);
        result.setCode(RespStatusCode.SUCCESS.getStatus());
        result.setSuccess(Boolean.TRUE);
        return result;
    }

    public static <T> ApiResult<T> fail(CommonErrorEnum errorEnum, String msg) {
        ApiResult<T> result = new ApiResult<T>();
        result.setSuccess(Boolean.FALSE);
        result.setCode(errorEnum.getStatusCode());
        result.setErrCode(errorEnum.getCode());
        result.setErrMsg(msg);
        return result;
    }

    public static <T> ApiResult<T> fail(CommonErrorEnum errorEnum, String msg, T data) {
        ApiResult<T> result = new ApiResult<T>();
        result.setSuccess(Boolean.FALSE);
        result.setCode(errorEnum.getStatusCode());
        result.setErrCode(errorEnum.getErrorCode());
        result.setErrMsg(msg);
        result.setData(data);
        return result;
    }

//    public static <T> ApiResult<T> fail(ErrorEnum errorEnum) {
//        ApiResult<T> result = new ApiResult<T>();
//        result.setSuccess(Boolean.FALSE);
//        result.setErrCode(errorEnum.getErrorCode());
//        result.setErrMsg(errorEnum.getErrorMsg());
//        return result;
//    }

    public boolean isSuccess() {
        return this.success;
    }
}
