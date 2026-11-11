package com.openchat.rtc.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 通话结束原因，统一结束写入、恢复结果判断和终态清理使用的业务标识。
 * 枚举名称保持现有数据库及接口字符串值；与 SSE 事件、日志事件和接口错误码分别维护。
 * 持久化模型及接口字段继续保留字符串，避免读取历史未知原因时丢失原值。
 */
public enum RtcEndReason {
    /** 单聊被叫拒接导致通话结束；群聊拒接仅更新本人的邀请状态。 */
    RTC_CALL_REJECTED,

    /** 发起方在响铃阶段取消通话；主动结束或重新呼出替换尚未接听的通话时也沿用此值。 */
    RTC_CALL_CANCELED,

    /** 已接听或已连接的通话正常结束；群聊仅在最后一名已接听参与者离开时使用。 */
    RTC_CALL_ENDED,

    /** 通话在邀请截止时间到达时仍未被接听，剩余邀请过期并使整通电话结束。 */
    RTC_CALL_EXPIRED,

    /** 用户主动恢复单聊时，当前设备与已保存的接听或发起设备不一致，服务端强制结束通话。 */
    DEVICE_MISMATCH,

    /** 用户主动恢复单聊时，原设备归属缺失或无法确认，服务端强制结束通话，不猜测或转移归属。 */
    DEVICE_IDENTITY_MISSING;

    /** 返回已有数据库和接口使用的字符串标识，避免全局枚举配置将其序列化为序号。 */
    @JsonValue
    public String getValue() {
        return name();
    }

    /**
     * 按普通终态取得默认结束原因，设备异常结束由调用方显式指定。
     *
     * @param state 已确认的通话终态，不能为空或活动状态
     * @return 与现有持久化值一致的默认结束原因
     * @throws IllegalArgumentException 状态为空或尚未结束
     */
    public static RtcEndReason fromState(RtcCallState state) {
        return switch (state) {
            case REJECTED -> RTC_CALL_REJECTED;
            case CANCELED -> RTC_CALL_CANCELED;
            case ENDED -> RTC_CALL_ENDED;
            case EXPIRED -> RTC_CALL_EXPIRED;
            case null, default -> throw new IllegalArgumentException("活动或空状态没有结束原因");
        };
    }

    /**
     * 识别数据库或接口快照中的既有结束原因，不修改原始字符串。
     *
     * @param value 原始原因字符串，允许为空
     * @return 精确匹配的枚举；空值或未知值返回 null，不推断为设备异常
     */
    public static RtcEndReason fromValue(String value) {
        for (RtcEndReason reason : values()) {
            if (reason.getValue().equals(value)) return reason;
        }
        return null;
    }
}
