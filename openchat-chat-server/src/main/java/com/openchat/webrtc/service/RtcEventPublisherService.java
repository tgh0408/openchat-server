package com.openchat.webrtc.service;

import com.openchat.rtc.domain.vo.RtcEvent;
import com.openchat.rtc.domain.RtcAuditEvent;
import com.openchat.rtc.port.RtcEventPublisher;
import com.openchat.common.utils.JsonUtils;
import com.openchat.webrtc.domain.dto.RtcPushMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Map;

/**
 * 将 RTC 事件可靠发布到 RocketMQ。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RtcEventPublisherService implements RtcEventPublisher {
    private final RtcNotificationSender sender;

    @Override
    public void publish(RtcEvent event) {
        RtcPushMessage message = new RtcPushMessage(
                event.eventId(),
                event.eventType(),
                event.callId(),
                event.roomId(),
                event.fromUid(),
                event.toUid(),
                event.callVersion(),
                event.signalSeq(),
                event.sentAt().toEpochMilli(),
                event.answeredDevice() == null ? event.payloadJson() : JsonUtils.toStr(Map.of(
                        "acceptedTerminal", event.answeredDevice().terminal(),
                        "acceptedDeviceId", event.answeredDevice().deviceId())));
        sender.send(message);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info("event={} callId={} eventId={} eventType={} toUid={}",
                            RtcAuditEvent.RTC_NOTIFICATION_REGISTERED, event.callId(), event.eventId(), event.eventType(), event.toUid());
                    if (event.answeredDevice() != null) {
                        log.info("event={} callId={} uid={} terminal={} deviceFingerprint={} committedVersion={}",
                                RtcAuditEvent.RTC_ACCEPT_COMMITTED, event.callId(), event.toUid(), event.answeredDevice().terminal(),
                                event.answeredDevice().fingerprint(), event.callVersion());
                    }
                }
            });
        }
    }
}
