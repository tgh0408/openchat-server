package com.openchat.user.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SystemRoleEnum {
    ADMIN(1, "管理员"),
    USER(2, "用户");

    @EnumValue
    @JsonValue
    private final Integer code;
    private final String desc;

}
