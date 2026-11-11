package com.openchat.webrtc.service;

import com.openchat.rtc.domain.RtcCallRecord;
import com.openchat.rtc.domain.RtcCallState;
import com.openchat.rtc.domain.RtcEventType;
import com.openchat.rtc.domain.vo.RtcEvent;
import com.openchat.rtc.port.RtcCallTerminalHandler;
import com.openchat.rtc.port.RtcEventPublisher;
import com.openchat.rtc.port.RtcIdGenerator;
import com.openchat.rtc.service.RtcCallConflictException;
import com.openchat.webrtc.dao.RtcCallDao;
import com.openchat.webrtc.dao.RtcMediaDao;
import com.openchat.webrtc.dao.RtcWebhookEventDao;
import com.openchat.webrtc.domain.entity.RtcWebhookEvent;
import com.openchat.webrtc.domain.enums.RtcMediaTransportEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** 将已验签媒体事件投影到通话记录，日志、状态和可靠通知由调用方同一事务提交。 */
@Service
@RequiredArgsConstructor
public class RtcWebhookCallService {
    private final RtcCallDao calls;
    private final RtcMediaDao media;
    private final RtcWebhookEventDao events;
    private final RtcMediaCleanupService liveKit;
    private final RtcCallTerminalHandler terminal;
    private final RtcEventPublisher publisher;
    private final RtcIdGenerator ids;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void apply(RtcCallRecord observed, RtcWebhookEvent event) {
        if (observed.state() != RtcCallState.ACCEPTED && observed.state() != RtcCallState.CONNECTED) return;
        var session = media.selectById(observed.callId());
        if (session == null || session.getTransport() != RtcMediaTransportEnum.SFU) return;
        boolean roomClosed = "room_finished".equals(event.getEventType())
                && !events.hasNewerRoom(event)
                && liveKit.isRoomClosed(observed.callId(), event.getRoomSid());
        // 网络查询先于通话条件写入；查询期间发生恢复、挂断或其他回调会令下面的版本校验失败。
        // 先协调所有节点对此通话的事件处理，再读取事件集合；挂断或成员变化也参与版本竞争。
        if (!calls.claimWebhook(observed)) throw new RtcCallConflictException();
        RtcCallRecord call = calls.findByCallId(observed.callId()).orElseThrow();
        Instant connectedAt = call.connectedAt();
        if (connectedAt == null) connectedAt = connectedAt(call, event.getRoomSid());
        Instant endedAt = null;
        if (roomClosed) {
            Instant occurredAt = Instant.ofEpochSecond(event.getOccurredAtSeconds());
            // LiveKit 时间只有秒精度；更早的结束事件不能结束后一次接听或连接。
            if ((call.acceptedAt() == null || event.getOccurredAtSeconds() >= call.acceptedAt().getEpochSecond())
                    && (connectedAt == null || event.getOccurredAtSeconds() >= connectedAt.getEpochSecond())) {
                endedAt = occurredAt;
                if (call.acceptedAt() != null && endedAt.isBefore(call.acceptedAt())) endedAt = call.acceptedAt();
                if (connectedAt != null && endedAt.isBefore(connectedAt)) endedAt = connectedAt;
            }
        }
        if (endedAt == null && Objects.equals(connectedAt, call.connectedAt())) return;
        if (!calls.updateFromWebhook(call, connectedAt, endedAt)) throw new RtcCallConflictException();
        if (endedAt == null) return;
        RtcCallRecord ended = calls.findByCallId(call.callId()).orElseThrow();
        terminal.onTerminal(ended, null);
        for (Long uid : ended.participantUids()) {
            publisher.publish(new RtcEvent(ids.nextId(), RtcEventType.RTC_CALL_ENDED,
                    ended.callId(), ended.roomId(), null, uid, ended.version(), null, Instant.now(), null));
        }
    }

    /** 按 roomSid + participantSid 还原区间；至少两个不同 UID 同时连接才确认接通。 */
    private Instant connectedAt(RtcCallRecord call, String roomSid) {
        var observations = events.listRoomEvents(call.callId(), roomSid);
        Map<String, RtcWebhookEvent> joins = new HashMap<>();
        Map<String, Long> leaves = new HashMap<>();
        for (var event : observations) {
            switch (event.getEventType()) {
                case "participant_joined" -> {
                    if (call.participantUids().contains(event.getUid())) {
                        joins.merge(event.getParticipantSid(), event, (first, next) ->
                                first.getOccurredAtSeconds() <= next.getOccurredAtSeconds() ? first : next);
                    }
                }
                case "participant_left" -> leaves.merge(event.getParticipantSid(),
                        event.getOccurredAtSeconds(), Math::min);
                default -> {
                    // 房间事件不代表任何成员已经连上媒体。
                }
            }
        }
        var boundaries = new ArrayList<Boundary>();
        long accepted = call.acceptedAt() == null ? 0 : call.acceptedAt().getEpochSecond();
        for (var join : joins.values()) {
            long start = Math.max(accepted, join.getOccurredAtSeconds());
            long end = leaves.getOrDefault(join.getParticipantSid(), Long.MAX_VALUE);
            if (start >= end) continue;
            boundaries.add(new Boundary(start, join.getUid(), 1));
            boundaries.add(new Boundary(end, join.getUid(), -1));
        }
        // 同秒退出先于加入；不能把秒精度下没有可证明重叠的区间算为接通。
        boundaries.sort(Comparator.comparingLong(Boundary::at).thenComparingInt(Boundary::delta));
        Map<Long, Integer> online = new HashMap<>();
        for (var boundary : boundaries) {
            int count = online.getOrDefault(boundary.uid(), 0) + boundary.delta();
            if (count == 0) online.remove(boundary.uid());
            else online.put(boundary.uid(), count);
            if (online.size() >= 2) {
                Instant connected = Instant.ofEpochSecond(boundary.at());
                return call.acceptedAt() != null && connected.isBefore(call.acceptedAt())
                        ? call.acceptedAt() : connected;
            }
        }
        return null;
    }

    private record Boundary(long at, Long uid, int delta) {}
}
