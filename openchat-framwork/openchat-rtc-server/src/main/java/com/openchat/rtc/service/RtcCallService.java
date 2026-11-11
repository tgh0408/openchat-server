package com.openchat.rtc.service;

import com.openchat.rtc.domain.RtcCallRecord;
import com.openchat.rtc.domain.RtcCallState;
import com.openchat.rtc.domain.RtcEndReason;
import com.openchat.rtc.domain.RtcEventType;
import com.openchat.rtc.domain.RtcParticipantState;
import com.openchat.rtc.domain.vo.*;
import com.openchat.rtc.port.RtcCallParticipants;
import com.openchat.rtc.port.RtcCallRepository;
import com.openchat.rtc.port.RtcCallTerminalHandler;
import com.openchat.rtc.port.RtcEventPublisher;
import com.openchat.rtc.port.RtcIdGenerator;
import com.openchat.rtc.port.RtcPermissionPort;

import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * RTC 呼叫状态机。
 */
public final class RtcCallService {
    private static final Duration CALL_TIMEOUT = Duration.ofMinutes(1);

    private final RtcCallRepository repository;
    private final RtcPermissionPort permission;
    private final RtcEventPublisher eventPublisher;
    private final RtcIdGenerator idGenerator;
    private final RtcCallTerminalHandler terminalHandler;
    private final Clock clock;
    private final RtcGroupCallService groups;

    public RtcCallService(
            RtcCallRepository repository,
            RtcPermissionPort permission,
            RtcEventPublisher eventPublisher,
            RtcIdGenerator idGenerator,
            RtcCallTerminalHandler terminalHandler) {
        this(repository, permission, eventPublisher, idGenerator, terminalHandler, Clock.systemUTC());
    }

    /** 时钟可注入以验证截止边界，数据库条件仍是最终并发保护。 */
    public RtcCallService(
            RtcCallRepository repository,
            RtcPermissionPort permission,
            RtcEventPublisher eventPublisher,
            RtcIdGenerator idGenerator,
            RtcCallTerminalHandler terminalHandler,
            Clock clock) {
        this.repository = repository;
        this.permission = permission;
        this.eventPublisher = eventPublisher;
        this.idGenerator = idGenerator;
        this.terminalHandler = terminalHandler;
        this.clock = clock;
        this.groups = new RtcGroupCallService(repository, permission, eventPublisher, idGenerator, terminalHandler, clock);
    }

    public RtcCallService(
            RtcCallRepository repository,
            RtcPermissionPort permission,
            RtcEventPublisher eventPublisher,
            RtcIdGenerator idGenerator) {
        this(repository, permission, eventPublisher, idGenerator, RtcCallTerminalHandler.noop());
    }

    public RtcCallView create(Long callerUid, CreateCallCommand command) {
        if (command.device() == null)
            throw new RtcOperationException(RtcOperationException.Reason.DEVICE_UNKNOWN);
        RtcCallParticipants participants = permission.requireCreate(callerUid, command);
        Optional<RtcCallRecord> existing =
                repository.findByCallerAndClientRequestId(callerUid, command.clientRequestId());
        if (existing.isPresent()) {
            permission.requireParticipant(existing.get(), callerUid);
            view(existing.get()).requireDevice(callerUid, command.device());
            return view(existing.get());
        }
        Instant now = clock.instant();
        RtcCallRecord record = new RtcCallRecord(
                idGenerator.nextId(),
                participants.roomId(),
                participants.callerUid(),
                participants.calleeUid(),
                command.mediaMode(),
                RtcCallState.RINGING,
                1,
                command.clientRequestId(),
                "room:" + participants.roomId(),
                now.plus(CALL_TIMEOUT),
                null,
                now,
                now,
                null,
                participants.participantUids()).withDevice(callerUid, command.device());
        if (!repository.insert(record)) {
            Optional<RtcCallRecord> sameRequest =
                    repository.findByCallerAndClientRequestId(callerUid, command.clientRequestId());
            if (sameRequest.isPresent()) {
                permission.requireParticipant(sameRequest.get(), callerUid);
                view(sameRequest.get()).requireDevice(callerUid, command.device());
                return view(sameRequest.get());
            }
            Optional<RtcCallRecord> activeCall = repository.findByActiveKey(record.activeKey());
            if (activeCall.isPresent()) {
                RtcCallRecord current = activeCall.get();
                permission.requireJoin(current, callerUid);
                current = expireIfNeeded(current);
                if (current.state() == RtcCallState.EXPIRED) {
                    if (repository.insert(record)) {
                        publishToParticipants(record, RtcEventType.RTC_CALL_CREATED, callerUid, null);
                        return view(record);
                    }
                    Optional<RtcCallRecord> replacement =
                            repository.findByActiveKey(record.activeKey());
                    if (replacement.isPresent()) {
                        permission.requireJoin(replacement.get(), callerUid);
                        return view(replacement.get());
                    }
                }
                return view(current);
            }
            throw new IllegalStateException("呼叫创建竞争未返回记录");
        }
        publishToParticipants(record, RtcEventType.RTC_CALL_CREATED, callerUid, null);
        return view(load(record.callId()));
    }

    public RtcCallView get(String callId, Long uid) {
        RtcCallRecord record = load(callId);
        if (permission.isGroup(record)) {
            permission.requireJoin(record, uid);
        } else {
            permission.requireParticipant(record, uid);
        }
        return view(record);
    }

    /** 发起端主动结束到期邀请；使用当前锁定状态，不能误挂断已接听成员。 */
    public RtcCallView expireInvitations(String callId, Long uid, CallActionCommand command) {
        RtcCallRecord record = load(callId);
        permission.requireParticipant(record, uid);
        if (!record.callerUid().equals(uid)) {
            throw new IllegalStateException("只有发起方可以结束到期邀请");
        }
        return view(expireIfNeeded(record));
    }

    public RtcCallView accept(String callId, Long uid, CallActionCommand command) {
        return respond(callId, uid, RtcParticipantState.ACCEPTED, command);
    }

    public RtcCallView reject(String callId, Long uid, CallActionCommand command) {
        return respond(callId, uid, RtcParticipantState.REJECTED, command);
    }

    /** 群成员可主动加入；单聊接听与拒绝仍只消费本人的 INVITED 状态。 */
    private RtcCallView respond(String callId, Long uid, RtcParticipantState response, CallActionCommand command) {
        RtcCallRecord record = load(callId);
        if (permission.isGroup(record)) return view(groups.respond(record, uid, response, command));
        permission.requireParticipant(record, uid);
        if (record.callerUid().equals(uid)) {
            throw new IllegalStateException("发起方不能响应自己的邀请");
        }
        record = expireIfNeeded(record);
        if (!record.state().terminal() && record.participantStates().get(uid) == RtcParticipantState.ACCEPTED) {
            view(record).requireDevice(uid, command.device());
            return view(record);
        }
        if (record.state().terminal()
                || record.participantStates().get(uid) != RtcParticipantState.INVITED) {
            if (response == RtcParticipantState.ACCEPTED
                    && (record.state().terminal() || record.participantStates().get(uid) != RtcParticipantState.ACCEPTED))
                throw new RtcOperationException(RtcOperationException.Reason.CALL_UNAVAILABLE);
            return view(record);
        }
        if (response == RtcParticipantState.ACCEPTED) {
            if (command.device() == null)
                throw new RtcOperationException(RtcOperationException.Reason.DEVICE_UNKNOWN);
            if (record.participantDevices().containsKey(uid))
                throw new RtcOperationException(RtcOperationException.Reason.DEVICE_UNKNOWN);
            record = record.withDevice(uid, command.device());
        }
        Map<Long, RtcParticipantState> states = new LinkedHashMap<>(record.participantStates());
        states.put(uid, response);
        RtcCallState nextState = record.state();
        RtcEventType eventType;
        switch (response) {
            case ACCEPTED -> {
                if (record.state() == RtcCallState.RINGING) {
                    nextState = RtcCallState.ACCEPTED;
                    eventType = RtcEventType.RTC_CALL_ACCEPTED;
                } else {
                    eventType = RtcEventType.RTC_CALL_PARTICIPANT_ACCEPTED;
                }
            }
            case REJECTED -> {
                Long callerUid = record.callerUid();
                boolean allDeclined = !permission.isGroup(record) && states.entrySet().stream()
                        .filter(entry -> !entry.getKey().equals(callerUid))
                        .allMatch(entry -> entry.getValue() == RtcParticipantState.REJECTED);
                if (allDeclined) {
                    nextState = RtcCallState.REJECTED;
                }
                eventType = allDeclined
                        ? RtcEventType.RTC_CALL_REJECTED : RtcEventType.RTC_CALL_PARTICIPANT_REJECTED;
            }
            default -> throw new IllegalArgumentException("无效的邀请响应");
        }
        RtcCallRecord saved = saveParticipants(record, states, nextState, eventType, uid,
                response == RtcParticipantState.ACCEPTED, false);
        if (response == RtcParticipantState.ACCEPTED) {
            eventPublisher.publish(new RtcEvent(idGenerator.nextId(),
                    RtcEventType.RTC_CALL_ANSWERED_ELSEWHERE, saved.callId(), saved.roomId(),
                    uid, uid, saved.version(), null, clock.instant(), null, command.device()));
        }
        return view(saved);
    }

    public RtcCallView cancel(String callId, Long uid, CallActionCommand command) {
        RtcCallRecord record = load(callId);
        permission.requireParticipant(record, uid);
        if (permission.isGroup(record)) {
            if (!record.callerUid().equals(uid)) {
                throw new IllegalStateException("只有发起方可以取消通话");
            }
            return view(groups.leave(record, uid, command));
        }
        return transition(record, uid, command, RtcCallState.RINGING, RtcCallState.CANCELED,
                RtcEventType.RTC_CALL_CANCELED, true);
    }

    public RtcCallView hangup(String callId, Long uid, CallActionCommand command) {
        RtcCallRecord record = load(callId);
        permission.requireParticipant(record, uid);
        if (record.state().terminal()) {
            return view(record);
        }
        if (permission.isGroup(record)) {
            return view(groups.leave(record, uid, command));
        }
        if (record.state() != RtcCallState.ACCEPTED
                && record.state() != RtcCallState.CONNECTED) {
            throw new IllegalStateException("当前状态不能挂断");
        }
        return update(callId, uid, command, record.state(), RtcCallState.ENDED,
                RtcEventType.RTC_CALL_ENDED, true);
    }

    /** 单聊用户主动结束优先于接听及恢复；调用方须确认真实单聊类型并管理冲突重试事务。 */
    public RtcCallView forceHangup(String callId, Long uid) {
        return forceHangup(callId, uid, null);
    }

    /** 已验证的恢复服务可指定设备不一致原因，普通 HTTP 操作不能自行提供原因。 */
    public RtcCallView forceHangup(String callId, Long uid, RtcEndReason reason) {
        RtcCallRecord record = load(callId);
        permission.requireParticipant(record, uid);
        if (record.state().terminal()) return view(record);
        if (permission.isGroup(record)) throw new IllegalStateException("群聊不能使用单聊强制挂断");
        RtcCallState nextState;
        RtcEventType eventType;
        switch (record.state()) {
            case RINGING -> {
                boolean caller = record.callerUid().equals(uid);
                nextState = caller ? RtcCallState.CANCELED : RtcCallState.REJECTED;
                eventType = caller ? RtcEventType.RTC_CALL_CANCELED : RtcEventType.RTC_CALL_REJECTED;
            }
            case ACCEPTED, CONNECTED -> {
                nextState = RtcCallState.ENDED;
                eventType = RtcEventType.RTC_CALL_ENDED;
            }
            default -> throw new IllegalStateException("无效的呼叫状态");
        }
        if (!repository.transition(callId, record.version(), record.state(), nextState,
                (reason == null ? RtcEndReason.fromState(nextState) : reason).getValue(), uid, clock.instant())) {
            // 不在当前事务重读：MySQL 可重复读及 MyBatis 会话缓存可能仍返回冲突前的快照。
            throw new RtcCallConflictException();
        }
        RtcCallRecord current = load(callId);
        terminalHandler.onTerminal(current, uid);
        publishToParticipants(current, eventType, uid, null);
        return view(current);
    }

    /** 呼出替换只作用于已确认记录；实际群类型由业务层缓存校验，不按人数推断。 */
    public RtcCallView endForRedial(String callId, Long uid, boolean group) {
        RtcCallRecord record = load(callId);
        permission.requireParticipant(record, uid);
        if (record.state().terminal()) return view(record);
        if (!group) {
            RtcCallState nextState = RtcCallState.ENDED;
            RtcEventType eventType = RtcEventType.RTC_CALL_ENDED;
            if (record.state() == RtcCallState.RINGING) {
                boolean caller = record.callerUid().equals(uid);
                nextState = caller ? RtcCallState.CANCELED : RtcCallState.REJECTED;
                eventType = caller ? RtcEventType.RTC_CALL_CANCELED : RtcEventType.RTC_CALL_REJECTED;
            }
            if (!repository.transition(callId, record.version(), record.state(), nextState,
                    RtcEndReason.fromState(nextState).getValue(), uid, clock.instant()))
                throw new RtcCallConflictException();
            RtcCallRecord ended = load(callId);
            terminalHandler.onTerminal(ended, uid);
            publishToParticipants(ended, eventType, uid, null);
            return view(ended);
        }
        return view(groups.endForRedial(record, uid));
    }

    /**
     * 群成员（含发起人）只退出本人；最后一名已接听成员离开才结束通话。
     */
    public RtcCallView leave(String callId, Long uid, CallActionCommand command) {
        RtcCallRecord record = load(callId);
        permission.requireParticipant(record, uid);
        if (!permission.isGroup(record)) {
            return view(record);
        }
        return view(groups.leave(record, uid, command));
    }

    private RtcCallView transition(
            RtcCallRecord record,
            Long uid,
            CallActionCommand command,
            RtcCallState expectedState,
            RtcCallState nextState,
            RtcEventType eventType,
            boolean terminal) {
        record = expireIfNeeded(record);
        if (record.state().terminal()) {
            return view(record);
        }
        if (expectedState == RtcCallState.RINGING
                && nextState == RtcCallState.ACCEPTED
                && (!record.participantUids().contains(uid) || record.callerUid().equals(uid))) {
            throw new IllegalStateException("只有被叫参与者可以接听");
        }
        if (expectedState == RtcCallState.RINGING
                && nextState == RtcCallState.CANCELED
                && !record.callerUid().equals(uid)) {
            throw new IllegalStateException("只有发起方可以取消通话");
        }
        if (nextState == RtcCallState.CANCELED)
            view(record).requireDevice(uid, command.device());
        if (!permission.isGroup(record)
                && expectedState == RtcCallState.RINGING
                && nextState == RtcCallState.REJECTED
                && !record.calleeUid().equals(uid)) {
            throw new IllegalStateException("当前用户不能执行该操作");
        }
        if (record.state() == nextState) {
            return view(record);
        }
        return update(record.callId(), uid, command, expectedState, nextState, eventType, terminal);
    }

    private RtcCallView update(
            String callId,
            Long uid,
            CallActionCommand command,
            RtcCallState expectedState,
            RtcCallState nextState,
            RtcEventType eventType,
            boolean terminal) {
        Instant now = clock.instant();
        boolean updated = repository.transition(
                callId,
                command.expectedVersion(),
                expectedState,
                nextState,
                terminal ? RtcEndReason.fromState(nextState).getValue() : null,
                terminal ? uid : null,
                now);
        if (!updated) {
            RtcCallRecord current = load(callId);
            if (current.state() == nextState) {
                return view(current);
            }
            throw new IllegalStateException("呼叫状态已变化，请刷新后重试");
        }
        RtcCallRecord current = load(callId);
        if (terminal) {
            terminalHandler.onTerminal(current, uid);
        }
        publishToParticipants(current, eventType, uid, null);
        return view(current);
    }

    private void publish(RtcCallRecord call, RtcEventType eventType, Long fromUid, Long toUid, String payload) {
        eventPublisher.publish(new RtcEvent(
                idGenerator.nextId(),
                eventType,
                call.callId(),
                call.roomId(),
                fromUid,
                toUid,
                call.version(),
                null,
                clock.instant(),
                payload));
    }

    private RtcCallRecord load(String callId) {
        return repository.findByCallId(callId)
                .orElseThrow(() -> new IllegalArgumentException("呼叫不存在"));
    }

    /** 供有事务边界的后台扫描调用，不使用 HTTP 当前身份。 */
    public void expire(String callId) {
        repository.findByCallId(callId).ifPresent(this::expireIfNeeded);
    }

    private RtcCallRecord expireIfNeeded(RtcCallRecord record) {
        if (permission.isGroup(record)) return groups.expire(record);
        if (record.state().terminal()
                || record.expiresAt() == null
                || record.expiresAt().isAfter(clock.instant())
                || !record.participantStates().containsValue(RtcParticipantState.INVITED)) {
            return record;
        }
        Map<Long, RtcParticipantState> states = new LinkedHashMap<>(record.participantStates());
        states.replaceAll((uid, state) -> state == RtcParticipantState.INVITED
                ? RtcParticipantState.EXPIRED : state);
        boolean unanswered = record.state() == RtcCallState.RINGING && !permission.isGroup(record);
        return saveParticipants(record, states,
                unanswered ? RtcCallState.EXPIRED : record.state(),
                unanswered ? RtcEventType.RTC_CALL_EXPIRED : RtcEventType.RTC_CALL_PARTICIPANTS_EXPIRED,
                null, false, true);
    }

    /** 只有成功写入的节点发送事件及终态摘要，二者沿用调用方事务的可靠发送。 */
    private RtcCallRecord saveParticipants(
            RtcCallRecord previous, Map<Long, RtcParticipantState> states,
            RtcCallState nextState, RtcEventType eventType, Long actorUid,
            boolean accepting, boolean expiring) {
        if (!repository.updateParticipants(previous, states, nextState,
                nextState.terminal() ? RtcEndReason.fromState(nextState).getValue() : null,
                nextState.terminal() ? actorUid : null,
                clock.instant(), accepting, expiring)) {
            throw new RtcCallConflictException();
        }
        RtcCallRecord current = load(previous.callId());
        if (current.state().terminal()) terminalHandler.onTerminal(current, actorUid);
        publishToParticipants(current, eventType, actorUid, null);
        return current;
    }

    private void publishToParticipants(
            RtcCallRecord call,
            RtcEventType eventType,
            Long fromUid,
            String payload) {
        for (Long uid : call.participantUids()) {
            publish(call, eventType, fromUid, uid, payload);
        }
    }

    private RtcCallView view(RtcCallRecord record) {
        return new RtcCallView(
                record.callId(),
                record.roomId(),
                record.callerUid(),
                record.calleeUid(),
                record.mediaMode(),
                record.state(),
                record.version(),
                record.expiresAt(),
                record.endReason(),
                record.createdAt(),
                record.updatedAt(),
                record.acceptedAt(),
                record.connectedAt(),
                record.endedAt(),
                record.durationSeconds(),
                record.endedByUid(),
                record.summaryMessageId(),
                record.participantUids().stream()
                        .map(uid -> new RtcCallParticipantView(
                                uid, record.participantStates().get(uid), record.participantVersions().get(uid)))
                        .toList(),
                com.openchat.rtc.domain.RtcDeviceOwnership.UNKNOWN,
                record.participantDevices());
    }
}
