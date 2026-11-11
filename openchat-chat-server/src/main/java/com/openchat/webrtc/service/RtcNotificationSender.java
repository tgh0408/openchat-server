package com.openchat.webrtc.service;

import com.openchat.common.constant.MQConstant;
import com.openchat.rtc.domain.RtcAuditEvent;
import com.openchat.transaction.annotation.SecureInvoke;
import com.openchat.transaction.service.MQProducer;
import com.openchat.webrtc.domain.dto.RtcPushMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 事务内只登记可靠任务，提交后异步投递，接听锁不等待 MQ 网络操作。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RtcNotificationSender {
    private final MQProducer producer;

    @SecureInvoke()
    public void send(RtcPushMessage message) {
        producer.sendSecureMsg(MQConstant.RTC_SSE_TOPIC, message, message.getEventId());
        log.info("event={} callId={} eventId={} eventType={} toUid={}",
                RtcAuditEvent.RTC_MQ_PUBLISHED, message.getCallId(), message.getEventId(), message.getEventType(), message.getToUid());
    }
}
