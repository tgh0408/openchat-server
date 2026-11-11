package com.openchat.user.domain.vo.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Data
public class ChangePasswordReq {

    @Schema(description = "旧密码")
    @NotNull
    private String oldPassword;

    @Schema(description = "新密码")
    @NotNull
    @Length(min = 8, max = 16)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+{}$$$$:;<>,.?~\\-=]).{6,18}$", message = "密码必须长度为6-18位并且包含大小写字母与特殊符号")
    private String newPassword;

    @Schema(description = "确认新密码")
    @NotNull
    private String confirmPassword;
}
