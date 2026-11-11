package com.openchat.transaction.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Description:
 * Author: 
 * Date: 2023-08-06
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecureInvokeDTO {
    //类名
    private String className;
    //方法名
    private String methodName;
    //参数类型
    private String parameterTypes;
    //参数
    private String args;
}
