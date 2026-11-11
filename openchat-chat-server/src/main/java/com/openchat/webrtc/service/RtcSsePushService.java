package com.openchat.webrtc.service;

import com.openchat.common.sse.core.SseEmitterManager;
import com.openchat.common.utils.JsonUtils;
import com.openchat.rtc.domain.RtcEventType;
import com.openchat.rtc.domain.RtcAuditEvent;
import com.openchat.webrtc.domain.dto.RtcPushMessage;
import com.openchat.webrtc.domain.vo.resp.RtcSseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;

/**
 * 将 RTC MQ 事件转换为现有资源 SSE 的 rtc 事件。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RtcSsePushService {
    private final SseEmitterManager sseEmitterManager;

    /** 校验并投递 RTC 事件；本人发起的创建通知不进入任何设备的 SSE。 */
    public void push(RtcPushMessage message) {
        if (!valid(message)) {
            log.warn("event={}", RtcAuditEvent.RTC_NOTIFICATION_INVALID);
            return;
        }
        // 创建事件只通知被叫，避免同账号其他设备把本人发起的呼叫当作新来电。
        if (message.getEventType() == RtcEventType.RTC_CALL_CREATED
                && message.getToUid().equals(message.getFromUid())) {
            return;
        }
        JsonNode payload = null;
        if (message.getPayloadJson() != null && !message.getPayloadJson().isBlank()) {
            try {
                // 沿用原有扩展数据的 Long 序列化规则，并保留显式 JSON null。
                payload = JsonUtils.getJsonMapper().valueToTree(
                        JsonUtils.toObj(message.getPayloadJson(), Object.class));
            } catch (RuntimeException exception) {
                log.warn("event={} callId={} eventId={}",
                        RtcAuditEvent.RTC_NOTIFICATION_INVALID, message.getCallId(), message.getEventId());
                return;
            }
        }
        RtcSseEvent event = new RtcSseEvent(message, payload);
        log.info("event={} callId={} eventId={} toUid={}",
                RtcAuditEvent.RTC_SSE_DISPATCH, message.getCallId(), message.getEventId(), message.getToUid());
        sseEmitterManager.sendEvent(
                message.getToUid(),
                RtcSseEvent.EVENT_NAME,
                message.getEventId(),
                JsonUtils.toStr(event));
    }

    /** 检查事件投递必需字段；事件类型由枚举限定，不再依赖字符串前缀。 */
    private boolean valid(RtcPushMessage message) {
        return message != null
                && message.getEventId() != null
                && !message.getEventId().isBlank()
                && message.getEventType() != null
                && message.getCallId() != null
                && message.getToUid() != null
                && message.getToUid() > 0
                && message.getCallVersion() != null
                && message.getCallVersion() > 0;
    }
}
