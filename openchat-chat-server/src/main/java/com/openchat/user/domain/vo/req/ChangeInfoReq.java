package com.openchat.user.domain.vo.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
public class ChangeInfoReq {
    @Schema(description = "用户名")
    @NotBlank
    @Length(max = 30, message = "用户名长度不能超过10个字符")
    private String name;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "性别")
    @NotNull
    private Integer sex;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "添加我的方式")
    private Integer addFunction;

    @Schema(description = "地区")
    private List<String> area;

    @Schema(description = "个性签名")
    @Length(max = 50 ,message = "个性签名太多啦,最多50个字符")
    private String personalitySignature;
}
