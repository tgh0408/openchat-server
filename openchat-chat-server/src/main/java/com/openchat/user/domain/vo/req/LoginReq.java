package com.openchat.user.domain.vo.req;

import com.openchat.user.domain.validate.interfaces.PhoneOrEmail;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.NotBlank;

@Data
public class LoginReq {
    private Long id;
    @NotBlank
    @PhoneOrEmail
    @Length(min = 6, max = 20,message = "用户账号必须为邮箱、手机")
    private String userId; //用户名
    @NotBlank
    @Length(min = 6, max = 20,message = "密码长度必须在6到20之间")
    private String password;
//    @NotBlank(message = "验证码ID不可为空")
    private String codeKey;
//    @NotBlank(message = "验证码信息不可为空")
    private String checkCode;

}

