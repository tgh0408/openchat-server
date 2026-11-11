package com.openchat.user.domain.vo.req;

import com.openchat.user.domain.validate.interfaces.PhoneOrEmail;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.multipart.MultipartFile;
@Data
public class RegisterReq {
    private Long id;
    @NotBlank
    @PhoneOrEmail
    @Length(min = 6, max = 20,message = "用户账号必须为邮箱、手机")
    private String userId; //用户名
    @NotBlank
    @Length(min = 1, max = 16,message = "用户名长度必须在1到16个字符之间")
    private String username; //用户名
    @NotBlank
    @Length(min = 6, max = 20,message = "密码长度必须在6到20之间")
    private String password;
    /** 仅随 multipart 注册表单上传，不接受客户端指定头像 URL。 */
    @JsonIgnore
    private MultipartFile avatar;
    // 注册暂不校验验证码，保留可选字段供现有客户端兼容。
    private String codeKey;
    private String checkCode;
}
