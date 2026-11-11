package com.openchat.webrtc.domain.vo.resp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.openchat.rtc.domain.RtcEventType;
import com.openchat.webrtc.domain.dto.RtcPushMessage;
import tools.jackson.databind.JsonNode;

/**
 * 资源 SSE 的 RTC 事件数据，字段名称与现有客户端协议保持一致。
 *
 * @param eventId 事件唯一标识，同时作为 SSE 的 id
 * @param eventType RTC 事件类型，序列化为字符串协议值
 * @param callId 呼叫唯一标识
 * @param roomId 呼叫所属聊天房间 ID，沿用全局 Long 转字符串规则
 * @param fromUid 操作人 UID；创建事件为主叫，系统超时事件可为空
 * @param toUid 接收者 UID
 * @param callVersion 呼叫状态版本
 * @param signalSeq 独立的媒体版本号，非媒体事件可为空
 * @param sentAt 事件产生时的 Unix 毫秒时间戳
 * @param payload 可选 JSON 扩展数据；未提供时省略该字段
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RtcSseEvent(
        String eventId,
        RtcEventType eventType,
        String callId,
        Long roomId,
        Long fromUid,
        Long toUid,
        Long callVersion,
        String signalSeq,
        Long sentAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode payload) {

    /** 资源 SSE 的事件通道名称，与客户端注册的监听事件一致。 */
    public static final String EVENT_NAME = "rtc";

    /**
     * 从消息对象直接构建 SSE 事件。
     * 注意：payload 需要从 message 里取，或由调用方另行传入。
     */
    public RtcSseEvent(RtcPushMessage message,JsonNode payload) {
        this(
                message.getEventId(),
                message.getEventType(),
                message.getCallId(),
                message.getRoomId(),
                message.getFromUid(),
                message.getToUid(),
                message.getCallVersion(),
                message.getSignalSeq(),
                message.getSentAt(),
                payload
        );
    }
}
