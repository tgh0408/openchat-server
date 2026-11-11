package com.openchat.webrtc.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 媒体信令类型，沿用客户端已有的字符串协议值。 */
public enum RtcMediaSignalTypeEnum {
    /** 主叫提交包含 ICE 候选的 SDP 提议。 */
    OFFER,
    /** 被叫提交与当前提议绑定的 SDP 应答。 */
    ANSWER,
    /** 从点对点传输不可逆地回退至服务器转发。 */
    FALLBACK,
    /** 上报本人摄像头开关状态，适用于两种传输模式。 */
    CAMERA;

    /** 固定输出枚举名称，避免全局配置将信令类型序列化为序号。 */
    @JsonValue
    public String getValue() {
        return name();
    }

    /** 只接受现有字符串值，不将数字或数字字符串解释为枚举序号。 */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static RtcMediaSignalTypeEnum fromValue(Object value) {
        if (!(value instanceof String name)) {
            throw new IllegalArgumentException("媒体信令类型必须是字符串");
        }
        return valueOf(name);
    }
}
