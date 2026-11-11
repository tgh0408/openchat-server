package com.openchat.user.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;


@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum BlackTypeEnum {
    UID(1,"UID"),
    IP(2,"IP");

    @EnumValue
    @JsonValue
    private Integer type;
    private String desc;


}
