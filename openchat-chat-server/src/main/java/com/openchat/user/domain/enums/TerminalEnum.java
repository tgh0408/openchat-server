package com.openchat.user.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum TerminalEnum {
    PC(1),
    PHONE(2),
    WEB(3);

    @JsonValue
    @EnumValue
    private final Integer value;
}
