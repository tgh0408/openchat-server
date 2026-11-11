package com.openchat.webrtc.domain.dto;

import com.openchat.rtc.domain.RtcEventType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RTC 跨节点 SSE 推送消息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RtcPushMessage implements Serializable {
    /** 事件唯一标识，沿用可靠调用和 SSE 的事件 ID。 */
    private String eventId;
    /** RTC 事件类型，在 MQ JSON 中保持字符串协议值。 */
    private RtcEventType eventType;
    /** 呼叫唯一标识。 */
    private String callId;
    /** 呼叫所属聊天房间 ID。 */
    private Long roomId;
    /** 事件操作人 UID；创建事件为主叫，系统超时事件可为空。 */
    private Long fromUid;
    /** 本条消息的接收者 UID，由各 SSE 节点投递到该账号的本机连接。 */
    private Long toUid;
    /** 呼叫状态版本，用于客户端识别过期状态。 */
    private Long callVersion;
    /** 独立的媒体版本号，仅媒体快照更新时携带。 */
    private String signalSeq;
    /** 事件产生时间，Unix 毫秒时间戳。 */
    private Long sentAt;
    /** 可选扩展数据的 JSON 文本，投递 SSE 时解析为 payload。 */
    private String payloadJson;
}
