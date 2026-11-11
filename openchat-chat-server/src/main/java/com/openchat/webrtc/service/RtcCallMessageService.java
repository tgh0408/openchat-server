package com.openchat.webrtc.service;

import com.openchat.chat.domain.entity.msg.RtcCallMsgDTO;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.vo.req.ChatMessageReq;
import com.openchat.chat.domain.vo.req.msg.RtcCallMsgReq;
import com.openchat.rtc.domain.RtcCallRecord;
import com.openchat.rtc.domain.RtcAuditEvent;
import com.openchat.rtc.domain.RtcMediaMode;
import com.openchat.rtc.domain.RtcEndReason;
import com.openchat.rtc.port.RtcCallTerminalHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class RtcCallMessageService implements RtcCallTerminalHandler {
    private final RtcCallSummaryPublisher summaryPublisher;
    private final RtcCameraStateService cameras;
    private final RtcMediaCleanupService mediaCleanup;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onTerminal(RtcCallRecord call, Long actorUid) {
        RtcEndReason reason = RtcEndReason.fromValue(call.endReason());
        if (reason == RtcEndReason.DEVICE_MISMATCH || reason == RtcEndReason.DEVICE_IDENTITY_MISSING) {
            mediaCleanup.closeRoom(call.callId());
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        log.warn("event={} callId={} uid={} reason={} committedVersion={}",
                                RtcAuditEvent.RTC_FORCED_END_COMMITTED, call.callId(), actorUid, call.endReason(), call.version());
                    }
                });
            }
        }
        if (call.mediaMode() == RtcMediaMode.VIDEO) {
            cameras.close(call.callId());
        }
        if (call.summaryMessageId() != null) {
            return;
        }

        RtcCallMsgReq body = RtcCallMsgReq.builder()
                .content(summaryContent(call))
                .rtcCall(RtcCallMsgDTO.builder()
                        .callId(call.callId())
                        .mediaMode(call.mediaMode().name())
                        .state(call.state().name())
                        .endReason(call.endReason())
                        .endedByUid(call.endedByUid())
                        .acceptedAt(toDate(call.acceptedAt()))
                        .endedAt(toDate(call.endedAt()))
                        .durationSeconds(call.durationSeconds())
                        .build())
                .build();
        ChatMessageReq request = ChatMessageReq.builder()
                .roomId(call.roomId())
                .msgType(MessageTypeEnum.RTC_CALL)
                .body(body)
                .clientMsgId(null)
                .build();

        summaryPublisher.publish(request, call.callerUid());
    }

    private String summaryContent(RtcCallRecord call) {
        return switch (call.mediaMode()) {
            case VIDEO -> "[视频通话]";
            case AUDIO -> "[语音通话]";
        };
    }

    private Date toDate(Instant value) {
        return value == null ? null : Date.from(value);
    }
}
