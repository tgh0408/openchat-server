package com.openchat.user.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum AddUserFunction {
    NEED_AGREE(1, "需要通过申请"),
    PASS(2, "直接添加");

    @EnumValue
    @JsonValue
    private final int code;
    private final String message;
}
