package com.openchat.rtc.domain.vo;

import com.openchat.rtc.domain.RtcEventType;
import com.openchat.rtc.domain.RtcDevice;

import java.time.Instant;

/**
 * RTC 实时事件。payloadJson 保持为 JSON 文本，避免核心模块依赖具体 JSON 库。
 *
 * @param eventId 事件唯一标识
 * @param eventType RTC 事件类型，使用统一的字符串协议枚举
 * @param callId 呼叫唯一标识
 * @param roomId 呼叫所属聊天房间 ID
 * @param fromUid 操作人 UID，系统超时事件可为空
 * @param toUid 本条事件的接收者 UID
 * @param callVersion 呼叫状态版本
 * @param signalSeq 独立的媒体版本号，非媒体事件可为空
 * @param sentAt 事件产生时间，由推送适配层转换为 Unix 毫秒时间戳
 * @param payloadJson 可选 JSON 扩展数据，不承载媒体内容
 */
public record RtcEvent(
        String eventId,
        RtcEventType eventType,
        String callId,
        Long roomId,
        Long fromUid,
        Long toUid,
        long callVersion,
        String signalSeq,
        Instant sentAt,
        String payloadJson,
        RtcDevice answeredDevice) {
    public RtcEvent(String eventId, RtcEventType eventType, String callId, Long roomId,
                    Long fromUid, Long toUid, long callVersion, String signalSeq,
                    Instant sentAt, String payloadJson) {
        this(eventId, eventType, callId, roomId, fromUid, toUid, callVersion, signalSeq,
                sentAt, payloadJson, null);
    }
}
