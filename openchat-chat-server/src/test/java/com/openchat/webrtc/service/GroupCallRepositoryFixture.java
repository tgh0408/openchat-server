package com.openchat.webrtc.service;

import com.openchat.rtc.domain.*;
import com.openchat.rtc.port.RtcCallRepository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 模拟数据库事务内的成员版本及当前参与状态，仅用于状态机竞争测试。 */
final class GroupCallRepositoryFixture {
    private final AtomicReference<RtcCallRecord> stored;
    private final ThreadLocal<RtcCallRecord> committed = new ThreadLocal<>();

    GroupCallRepositoryFixture(RtcCallRepository repository, AtomicReference<RtcCallRecord> stored) {
        this.stored = stored;
        when(repository.updateGroupParticipant(any(), anyLong(), any(), nullable(RtcDevice.class),
                anyLong(), any())).thenAnswer(invocation -> update(
                        invocation.getArgument(1), invocation.getArgument(2),
                        invocation.getArgument(3), invocation.getArgument(4), invocation.getArgument(5)));
    }

    Optional<RtcCallRecord> read() {
        var own = committed.get();
        committed.remove();
        return Optional.ofNullable(own == null ? stored.get() : own);
    }

    static RtcCallRecord group(RtcCallRecord call) {
        Map<Long, Integer> versions = new LinkedHashMap<>();
        call.participantUids().forEach(uid -> versions.put(uid, 1));
        return new RtcCallRecord(call.callId(), call.roomId(), call.callerUid(), null,
                call.mediaMode(), call.state(), call.version(), call.clientRequestId(), call.activeKey(),
                call.expiresAt(), call.endReason(), call.createdAt(), call.updatedAt(), call.endedAt(),
                call.acceptedAt(), call.connectedAt(), call.durationSeconds(), call.endedByUid(),
                call.summaryMessageId(), call.participantUids(), call.participantStates(),
                call.participantDevices(), versions);
    }

    private synchronized boolean update(
            Long uid, RtcParticipantState next, RtcDevice device, long version, Instant now) {
        var current = stored.get();
        if (current.state().terminal() || current.participantVersions().getOrDefault(uid, 1) != version)
            return false;
        var states = new LinkedHashMap<>(current.participantStates());
        var devices = new LinkedHashMap<>(current.participantDevices());
        var versions = new LinkedHashMap<>(current.participantVersions());
        var uids = new ArrayList<>(current.participantUids());
        boolean existing = states.containsKey(uid);
        if (!existing) uids.add(uid);
        states.put(uid, next);
        versions.put(uid, existing ? (int) version + 1 : 1);
        if (next == RtcParticipantState.ACCEPTED) devices.put(uid, device);
        var state = current.state();
        Instant accepted = current.acceptedAt();
        if (next == RtcParticipantState.ACCEPTED && state == RtcCallState.RINGING) {
            state = RtcCallState.ACCEPTED;
            accepted = now;
        }
        if (!states.containsValue(RtcParticipantState.ACCEPTED))
            state = accepted == null ? RtcCallState.CANCELED : RtcCallState.ENDED;
        var updated = new RtcCallRecord(current.callId(), current.roomId(), current.callerUid(), null,
                current.mediaMode(), state, current.version() + 1, current.clientRequestId(),
                state.terminal() ? null : current.activeKey(), current.expiresAt(),
                state.terminal() ? RtcEndReason.fromState(state).getValue() : null,
                current.createdAt(), now, state.terminal() ? now : null, accepted,
                current.connectedAt(), null, state.terminal() ? uid : null, null,
                uids, states, devices, versions);
        stored.set(updated);
        // 同一事务返回自身写入后的快照，避免其他模拟节点提交后的状态串入响应。
        committed.set(updated);
        return true;
    }
}
