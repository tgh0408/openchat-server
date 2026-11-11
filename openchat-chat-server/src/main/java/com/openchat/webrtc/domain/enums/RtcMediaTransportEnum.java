package com.openchat.webrtc.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 媒体传输模式，数据库和接口继续使用 P2P、SFU 字符串。 */
@Getter
@AllArgsConstructor
public enum RtcMediaTransportEnum {
    /** 单聊双方直接传输媒体。 */
    P2P("P2P"),
    /** 通过媒体服务器转发，回退后不再切回点对点传输。 */
    SFU("SFU");

    /** 数据库存储值与接口协议值，禁止使用枚举序号。 */
    @EnumValue
    @JsonValue
    private final String value;
}
