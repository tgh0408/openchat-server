package com.openchat.rtc.service;

import com.openchat.rtc.domain.*;
import com.openchat.rtc.domain.vo.*;
import com.openchat.rtc.port.*;

import java.time.Clock;
import java.util.Objects;

/** 群通话的个人状态机；不提交整通成员快照，不使用整通旧版本裁决个人操作。 */
final class RtcGroupCallService {
    private final RtcCallRepository repository;
    private final RtcPermissionPort permission;
    private final RtcEventPublisher events;
    private final RtcIdGenerator ids;
    private final RtcCallTerminalHandler terminal;
    private final Clock clock;

    RtcGroupCallService(RtcCallRepository repository, RtcPermissionPort permission,
            RtcEventPublisher events, RtcIdGenerator ids, RtcCallTerminalHandler terminal, Clock clock) {
        this.repository = repository;
        this.permission = permission;
        this.events = events;
        this.ids = ids;
        this.terminal = terminal;
        this.clock = clock;
    }

    RtcCallRecord respond(RtcCallRecord call, Long uid, RtcParticipantState next, CallActionCommand command) {
        if (next == RtcParticipantState.ACCEPTED) {
            permission.requireJoin(call, uid);
            if (call.state().terminal()) throw new RtcOperationException(RtcOperationException.Reason.CALL_UNAVAILABLE);
            if (command.device() == null) throw new RtcOperationException(RtcOperationException.Reason.DEVICE_UNKNOWN);
            if (call.participantStates().get(uid) == RtcParticipantState.ACCEPTED) {
                requireDevice(call, uid, command.device());
                return call;
            }
            var saved = save(call, uid, next, command.device(), command.expectedVersion(),
                    RtcEventType.RTC_CALL_PARTICIPANT_ACCEPTED);
            events.publish(new RtcEvent(ids.nextId(), RtcEventType.RTC_CALL_ANSWERED_ELSEWHERE,
                    saved.callId(), saved.roomId(), uid, uid, saved.version(), null,
                    clock.instant(), null, command.device()));
            return saved;
        }
        permission.requireParticipant(call, uid);
        if (call.state().terminal() || call.participantStates().get(uid) != RtcParticipantState.INVITED) return call;
        return save(call, uid, RtcParticipantState.REJECTED, null, command.expectedVersion(),
                RtcEventType.RTC_CALL_PARTICIPANT_REJECTED);
    }

    RtcCallRecord leave(RtcCallRecord call, Long uid, CallActionCommand command) {
        permission.requireParticipant(call, uid);
        if (call.state().terminal() || call.participantStates().get(uid) != RtcParticipantState.ACCEPTED) return call;
        requireDevice(call, uid, command.device());
        return save(call, uid, RtcParticipantState.LEFT, command.device(), command.expectedVersion(),
                RtcEventType.RTC_CALL_PARTICIPANT_LEFT);
    }

    /** 已确认的强制呼出使用服务端读取的本人成员版本，不能影响其他成员。 */
    RtcCallRecord endForRedial(RtcCallRecord call, Long uid) {
        permission.requireParticipant(call, uid);
        RtcParticipantState state = call.participantStates().get(uid);
        if (call.state().terminal() || (state != RtcParticipantState.INVITED && state != RtcParticipantState.ACCEPTED))
            return call;
        return save(call, uid,
                state == RtcParticipantState.INVITED ? RtcParticipantState.REJECTED : RtcParticipantState.LEFT,
                call.participantDevices().get(uid), call.participantVersions().get(uid),
                state == RtcParticipantState.INVITED
                        ? RtcEventType.RTC_CALL_PARTICIPANT_REJECTED : RtcEventType.RTC_CALL_PARTICIPANT_LEFT);
    }

    RtcCallRecord expire(RtcCallRecord call) {
        if (call.state().terminal() || call.expiresAt().isAfter(clock.instant())) return call;
        boolean changed = false;
        for (Long uid : call.participantUids()) {
            if (call.participantStates().get(uid) != RtcParticipantState.INVITED) continue;
            if (!repository.updateGroupParticipant(call, uid, RtcParticipantState.EXPIRED, null,
                    call.participantVersions().get(uid), clock.instant())) throw new RtcCallConflictException();
            changed = true;
        }
        if (!changed) return call;
        RtcCallRecord saved = load(call.callId());
        publish(saved, null, RtcEventType.RTC_CALL_PARTICIPANTS_EXPIRED);
        return saved;
    }

    private RtcCallRecord save(RtcCallRecord call, Long uid, RtcParticipantState next,
            RtcDevice device, long version, RtcEventType type) {
        requireVersion(call, uid, version);
        if (!repository.updateGroupParticipant(call, uid, next, device, version, clock.instant()))
            throw new RtcCallConflictException();
        RtcCallRecord saved = load(call.callId());
        if (saved.state().terminal()) {
            terminal.onTerminal(saved, uid);
            type = saved.state() == RtcCallState.CANCELED
                    ? RtcEventType.RTC_CALL_CANCELED : RtcEventType.RTC_CALL_ENDED;
        } else if (next == RtcParticipantState.ACCEPTED && call.state() == RtcCallState.RINGING) {
            type = RtcEventType.RTC_CALL_ACCEPTED;
        }
        publish(saved, uid, type);
        return saved;
    }

    private void requireVersion(RtcCallRecord call, Long uid, long version) {
        if (call.participantVersions().getOrDefault(uid, 1) != version) throw new RtcCallConflictException();
    }

    private void requireDevice(RtcCallRecord call, Long uid, RtcDevice device) {
        RtcDevice owner = call.participantDevices().get(uid);
        if (owner == null || device == null) throw new RtcOperationException(RtcOperationException.Reason.DEVICE_UNKNOWN);
        if (!Objects.equals(owner, device)) throw new RtcOperationException(RtcOperationException.Reason.ACCEPTED_ELSEWHERE);
    }

    private RtcCallRecord load(String callId) {
        return repository.findByCallId(callId).orElseThrow(() -> new IllegalArgumentException("呼叫不存在"));
    }

    private void publish(RtcCallRecord call, Long actor, RtcEventType type) {
        for (Long uid : call.participantUids()) {
            events.publish(new RtcEvent(ids.nextId(), type, call.callId(), call.roomId(),
                    actor, uid, call.version(), null, clock.instant(), null));
        }
    }
}
