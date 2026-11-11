package com.openchat.user.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum ApplyTypeEnum {

    ADD_FRIEND(1,"加好友"),
    ADD_GROUP(2,"加群");

    @EnumValue
    @JsonValue
    private Integer status;
    private String desc;
}
