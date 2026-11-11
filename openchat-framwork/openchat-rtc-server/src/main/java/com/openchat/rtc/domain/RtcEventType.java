package com.openchat.rtc.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * RTC 实时事件类型，供呼叫状态机、MQ 和 SSE 共用。
 * 枚举名称即现有协议值，不使用序号编码，避免影响已部署的客户端及待消费消息。
 */
public enum RtcEventType {
    /** 创建呼叫，通知被邀请者。 */
    RTC_CALL_CREATED,
    /** 首位被叫接听，呼叫进入已接听状态。 */
    RTC_CALL_ACCEPTED,
    /** 只通知接听用户本人，各设备按归属决定是否本地收起。 */
    RTC_CALL_ANSWERED_ELSEWHERE,
    /** 全部被叫拒绝，呼叫结束。 */
    RTC_CALL_REJECTED,
    /** 主叫取消尚未接听的呼叫。 */
    RTC_CALL_CANCELED,
    /** 已接听的呼叫被挂断。 */
    RTC_CALL_ENDED,
    /** 无人接听且邀请到期，呼叫结束。 */
    RTC_CALL_EXPIRED,
    /** 呼叫已被接听后，另一位参与者接受邀请。 */
    RTC_CALL_PARTICIPANT_ACCEPTED,
    /** 部分参与者拒绝邀请，其他参与者的呼叫继续。 */
    RTC_CALL_PARTICIPANT_REJECTED,
    /** 群聊成员离开，不结束其他成员的通话。 */
    RTC_CALL_PARTICIPANT_LEFT,
    /** 呼叫已被接听，剩余未响应的邀请到期。 */
    RTC_CALL_PARTICIPANTS_EXPIRED,
    /** 媒体协商快照更新，通过 signalSeq 通知媒体版本，不携带 SDP。 */
    RTC_MEDIA_UPDATED,
    /** 参与者摄像头实际开关变化，payload 为 enabled/version，signalSeq 为该成员版本。 */
    RTC_CAMERA_UPDATED;

    /** 固定使用字符串协议值，不受全局枚举序号序列化配置影响。 */
    @JsonValue
    public String getValue() {
        return name();
    }
}
