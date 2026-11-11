package com.openchat.webrtc.service;

import com.openchat.rtc.port.RtcCallRepository;
import com.openchat.webrtc.constant.LiveKitRoomKey;
import com.openchat.webrtc.dao.RtcWebhookEventDao;
import com.openchat.webrtc.domain.entity.RtcWebhookEvent;
import livekit.LivekitWebhook.WebhookEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Date;

/** 持久化已验签的媒体观测；媒体生命周期与业务通话状态分别保存。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RtcWebhookService {
    private final RtcCallRepository calls;
    private final RtcWebhookEventDao events;
    private final RtcWebhookCallService callUpdates;

    /** 事件主键处理重投，独立事件行保留乱序、房间重建和成员重连的会话边界。 */
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void handle(WebhookEvent event) {
        switch (event.getEvent()) {
            case "room_started", "participant_joined", "participant_left", "room_finished" -> observe(event);
            default -> {
                // 不消费的轨道、录制及未来新增事件直接确认。
            }
        }
    }

    private void observe(WebhookEvent event) {
        if (!event.hasRoom() || event.getRoom().getName().isBlank()) {
            throw invalidEvent();
        }
        String roomName = event.getRoom().getName();
        if (!roomName.startsWith(LiveKitRoomKey.roomKey)) return;
        String callId = roomName.substring(LiveKitRoomKey.roomKey.length());
        if (!validIdentifier(callId, 64) || !validIdentifier(event.getId(), 128)
                || !validIdentifier(event.getRoom().getSid(), 128) || event.getCreatedAt() <= 0
                || event.getCreatedAt() > 253402300799L) {
            throw invalidEvent();
        }
        boolean participantEvent = event.getEvent().startsWith("participant_");
        if (participantEvent && (!event.hasParticipant()
                || !validIdentifier(event.getParticipant().getSid(), 128)
                || event.getParticipant().getIdentity().isBlank())) {
            throw invalidEvent();
        }
        var call = calls.findByCallId(callId);
        if (call.isEmpty()) {
            log.warn("LiveKit webhook ignored, eventId={} callId={} reason=unknown_call",
                    safe(event.getId()), safe(callId));
            return;
        }
        String identity = event.getParticipant().getIdentity();
        if (participantEvent && call.get().participantUids().stream()
                .noneMatch(uid -> uid.toString().equals(identity))) {
            log.warn("LiveKit webhook ignored, eventId={} callId={} reason=unknown_participant",
                    safe(event.getId()), safe(callId));
            return;
        }
        Instant occurredAt = Instant.ofEpochSecond(event.getCreatedAt());
        Instant receivedAt = Instant.now();
        RtcWebhookEvent stored = new RtcWebhookEvent();
        stored.setEventId(event.getId());
        stored.setCallId(callId);
        stored.setEventType(event.getEvent());
        stored.setRoomSid(event.getRoom().getSid());
        stored.setOccurredAtSeconds(event.getCreatedAt());
        stored.setReceivedAt(Date.from(receivedAt));
        long roomCreatedAt = event.getRoom().getCreationTime();
        if (roomCreatedAt > 0 && roomCreatedAt <= event.getCreatedAt()) {
            stored.setRoomCreatedAtSeconds(roomCreatedAt);
        }
        if (participantEvent) {
            stored.setParticipantSid(event.getParticipant().getSid());
            stored.setUid(Long.valueOf(identity));
        }
        // 日志、通话更新和可靠通知同事务；失败时不能留下导致重投被跳过的 eventId。
        if (!events.insertIfAbsent(stored)) return;
        callUpdates.apply(call.get(), stored);
        switch (event.getEvent()) {
            case "participant_joined" -> log.info(
                    "LiveKit participant_joined eventId={} callId={} roomSid={} participantSid={} uid={} mediaConnectedAt={} receivedAt={}",
                    safe(event.getId()), safe(callId), safe(event.getRoom().getSid()),
                    safe(event.getParticipant().getSid()), safe(identity), occurredAt, receivedAt);
            case "participant_left" -> log.info(
                    "LiveKit participant_left eventId={} callId={} roomSid={} participantSid={} uid={} mediaLeftAt={} receivedAt={}",
                    safe(event.getId()), safe(callId), safe(event.getRoom().getSid()),
                    safe(event.getParticipant().getSid()), safe(identity), occurredAt, receivedAt);
            case "room_started" -> log.info(
                    "LiveKit room_started eventId={} callId={} roomSid={} roomStartedAt={} receivedAt={}",
                    safe(event.getId()), safe(callId), safe(event.getRoom().getSid()), occurredAt, receivedAt);
            case "room_finished" -> {
                long createdAt = event.getRoom().getCreationTime();
                Long roomLifetimeSeconds = createdAt > 0 && createdAt <= event.getCreatedAt()
                        ? event.getCreatedAt() - createdAt : null;
                var record = call.get();
                log.info("LiveKit room_finished eventId={} callId={} roomSid={} roomFinishedAt={} roomLifetimeSeconds={} acceptedAt={} connectedAt={} endedAt={} durationSeconds={} receivedAt={}",
                        safe(event.getId()), safe(callId), safe(event.getRoom().getSid()), occurredAt,
                        roomLifetimeSeconds, record.acceptedAt(), record.connectedAt(),
                        record.endedAt(), record.durationSeconds(), receivedAt);
            }
            default -> throw invalidEvent();
        }
    }

    private boolean validIdentifier(String value, int maxLength) {
        return !value.isBlank() && value.length() <= maxLength;
    }

    /** 日志只包含必要标识和时间，不记录参与者 metadata 或原始请求。 */
    private String safe(String value) {
        return StringUtils.truncate(StringUtils.replaceChars(value, "\r\n\t", "___"), 160);
    }

    private ResponseStatusException invalidEvent() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的 LiveKit 事件");
    }
}
