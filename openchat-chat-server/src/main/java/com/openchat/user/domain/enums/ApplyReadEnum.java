package com.openchat.user.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum ApplyReadEnum {
    UNREAD(1,"未读"),
    READ(2,"已读");

    @EnumValue
    @JsonValue
    private Integer value;
    private String desc;
}
