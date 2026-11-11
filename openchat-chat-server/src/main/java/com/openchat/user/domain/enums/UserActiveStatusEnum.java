package com.openchat.user.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum UserActiveStatusEnum {
    ON_LINE(1,"在线"),
    OFF_LINE(2,"离线");
    @EnumValue
    @JsonValue
    private final Integer status;
    private final String  desc;
}
