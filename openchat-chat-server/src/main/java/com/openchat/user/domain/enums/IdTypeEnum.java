package com.openchat.user.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum IdTypeEnum {
    UNKNOWN(0),
    PHONE(1),
    EMAIL(2),
    ID(3);

    @JsonValue
    @EnumValue
    private final Integer code;
}
