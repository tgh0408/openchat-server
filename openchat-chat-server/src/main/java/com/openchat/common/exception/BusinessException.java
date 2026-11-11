package com.openchat.common.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class BusinessException extends RuntimeException{
    protected Integer errorCode;
    protected String errorMessage;
    public BusinessException(String message){
        super(message);
        this.errorCode = CommonErrorEnum.BUSINESS_ERROR.getCode();
        this.errorMessage = message;
    }
    public BusinessException(Integer errorCode,String message){
        super(message);
        this.errorCode = errorCode;
        this.errorMessage = message;
    }
    public BusinessException(ErrorEnum errorEnum){
        super(errorEnum.getErrorMsg());
        this.errorCode = errorEnum.getErrorCode();
        this.errorMessage = errorEnum.getErrorMsg();
    }
}
