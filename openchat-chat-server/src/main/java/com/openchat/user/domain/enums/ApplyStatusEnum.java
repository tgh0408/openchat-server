package com.openchat.user.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum ApplyStatusEnum {
    WAIT_APPROVE(1,"等待审批"),
    APPROVED(2,"已通过"),
    REJECTED(3,"已拒绝");

    @EnumValue
    @JsonValue
    private Integer status;
    private String desc;
}
