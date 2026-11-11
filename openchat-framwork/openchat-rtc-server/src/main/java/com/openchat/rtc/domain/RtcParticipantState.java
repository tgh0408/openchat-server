package com.openchat.rtc.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 每位参与者的邀请响应；不同成员的响应不能互相覆盖。
 */
@AllArgsConstructor
@Getter
public enum RtcParticipantState {
    INVITED(1),
    ACCEPTED(2),
    REJECTED(3),
    EXPIRED(4),
    LEFT(5);

    @JsonValue
    @EnumValue
    private final int value;

}
