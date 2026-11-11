package com.openchat.webrtc.service;

import com.openchat.chat.dao.GroupMemberDao;
import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.service.RoomAccessService;
import com.openchat.rtc.domain.*;
import com.openchat.rtc.domain.vo.CallActionCommand;
import com.openchat.rtc.domain.vo.RtcEvent;
import com.openchat.rtc.port.*;
import com.openchat.rtc.service.RtcCallConflictException;
import com.openchat.rtc.service.RtcCallService;
import com.openchat.rtc.service.RtcOperationException;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** 两个状态机共享原子存储替身，检查群成员退出与接听竞争；不连接外部服务。 */
public class RtcGroupDepartureTest {
    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");
    private static final RtcDevice DEVICE = new RtcDevice("PC", "a".repeat(64));
    private final AtomicReference<RtcCallRecord> stored = new AtomicReference<>();
    private final List<RtcEvent> events = new CopyOnWriteArrayList<>();
    private RtcCallRepository repository;
    private RtcPermissionPort permission;
    private RtcCallTerminalHandler terminal;
    private RtcCallService calls;
    private GroupCallRepositoryFixture fixture;

    @Before
    public void setUp() {
        repository = mock(RtcCallRepository.class);
        terminal = mock(RtcCallTerminalHandler.class);
        var access = mock(RoomAccessService.class);
        var room = new Room();
        room.setType(RoomTypeEnum.GROUP);
        when(access.requireRoom(100L)).thenReturn(room);
        permission = new RtcPermissionService(access, mock(GroupMemberDao.class));
        calls = node();
        fixture = new GroupCallRepositoryFixture(repository, stored);
        when(repository.findByCallId("call")).thenAnswer(_ -> fixture.read());
        when(repository.updateParticipants(any(), anyMap(), any(), nullable(String.class),
                nullable(Long.class), any(), anyBoolean(), anyBoolean())).thenAnswer(invocation -> {
            RtcCallRecord previous = invocation.getArgument(0);
            RtcCallRecord current = stored.get();
            if (!previous.version().equals(current.version())) return false;
            return stored.compareAndSet(current, copy(previous, invocation.getArgument(1),
                    invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4)));
        });
    }

    @Test
    public void callerHangupLeavesRemainingMembersIncludingTwoPersonGroup() {
        for (List<Long> uids : List.of(List.of(10L, 20L), List.of(10L, 20L, 30L))) {
            stored.set(record(RtcCallState.CONNECTED, uids));
            assertEquals(RtcCallState.CONNECTED, calls.hangup("call", 10L, command()).state());
            assertEquals(RtcParticipantState.LEFT, stored.get().participantStates().get(10L));
            assertEquals(RtcParticipantState.ACCEPTED, stored.get().participantStates().get(20L));
            assertEquals("room:100", stored.get().activeKey());
        }
        assertTrue(events.stream().allMatch(event -> event.eventType() == RtcEventType.RTC_CALL_PARTICIPANT_LEFT));
        verifyNoInteractions(terminal);
    }

    @Test
    public void onlyLastDepartureEndsCallAndDuplicateDoesNotRepeatEffects() {
        stored.set(record(RtcCallState.ACCEPTED, List.of(10L, 20L, 30L)));
        calls.leave("call", 10L, command());
        calls.leave("call", 20L, command());
        calls.leave("call", 20L, command());
        verifyNoInteractions(terminal);
        assertEquals(RtcCallState.ENDED, calls.leave("call", 30L, command()).state());
        calls.hangup("call", 30L, command());
        assertNull(stored.get().activeKey());
        assertEquals(Long.valueOf(30L), stored.get().endedByUid());
        assertEquals(3, events.stream().filter(event -> event.eventType() == RtcEventType.RTC_CALL_ENDED).count());
        verify(terminal).onTerminal(any(), eq(30L));
    }

    @Test
    public void pendingInvitationsCannotKeepEmptyRoomAlive() {
        stored.set(record(RtcCallState.RINGING, List.of(10L, 20L, 30L)));
        assertEquals(RtcCallState.CANCELED, calls.cancel("call", 10L, command()).state());
        assertEquals(RtcParticipantState.LEFT, stored.get().participantStates().get(10L));
        assertThrows(RtcOperationException.class, () -> calls.accept("call", 20L, command()));
        verify(terminal).onTerminal(any(), eq(10L));
    }

    @Test
    public void lateCancelAfterAcceptanceOnlyLeavesCaller() {
        stored.set(record(RtcCallState.ACCEPTED, List.of(10L, 20L)));
        assertEquals(RtcCallState.ACCEPTED, calls.cancel("call", 10L, command()).state());
        verifyNoInteractions(terminal);
    }

    @Test
    public void lastConnectedMemberEndsCallEvenWithUnansweredInvitations() {
        var call = record(RtcCallState.ACCEPTED, List.of(10L, 20L, 30L));
        stored.set(copy(call, Map.of(10L, RtcParticipantState.LEFT,
                20L, RtcParticipantState.ACCEPTED, 30L, RtcParticipantState.INVITED),
                call.state(), null, null));
        assertEquals(RtcCallState.ENDED, calls.leave("call", 20L, command()).state());
        verify(terminal).onTerminal(any(), eq(20L));
    }

    @Test
    public void rejectionAndExpiryKeepCallerPresent() {
        stored.set(record(RtcCallState.RINGING, List.of(10L, 20L)));
        assertEquals(RtcCallState.RINGING, calls.reject("call", 20L, command()).state());
        var ringing = record(RtcCallState.RINGING, List.of(10L, 20L, 30L));
        stored.set(ringing);
        var expiry = new RtcCallService(repository, permission, events::add,
                () -> UUID.randomUUID().toString(), terminal,
                Clock.fixed(NOW.plusSeconds(61), ZoneOffset.UTC));
        expiry.expire("call");
        assertEquals(RtcCallState.RINGING, stored.get().state());
        assertEquals(RtcParticipantState.ACCEPTED, stored.get().participantStates().get(10L));
        assertEquals(RtcParticipantState.EXPIRED, stored.get().participantStates().get(20L));
        int eventCount = events.size();
        expiry.expire("call");
        assertEquals(eventCount, events.size());
        verifyNoInteractions(terminal);
    }

    @Test
    public void callerRedialLeavesPeersAndLastMemberRedialEndsOldCall() {
        stored.set(record(RtcCallState.ACCEPTED, List.of(10L, 20L)));
        assertEquals(RtcCallState.ACCEPTED, calls.endForRedial("call", 10L, true).state());
        verifyNoInteractions(terminal);
        assertEquals(RtcCallState.ENDED, calls.endForRedial("call", 20L, true).state());
        verify(terminal).onTerminal(any(), eq(20L));
    }

    @Test
    public void wrongDeviceCannotLeaveAndNonCallerCannotCancel() {
        stored.set(record(RtcCallState.ACCEPTED, List.of(10L, 20L)));
        var other = new CallActionCommand("leave", 1, new RtcDevice("PHONE", "b".repeat(64)));
        assertThrows(RtcOperationException.class, () -> calls.leave("call", 10L, other));
        assertThrows(IllegalStateException.class, () -> calls.cancel("call", 20L, command()));
        assertThrows(IllegalStateException.class, () -> calls.forceHangup("call", 10L));
        verifyNoInteractions(terminal);
        assertTrue(events.isEmpty());
    }

    @Test
    public void concurrentDifferentMemberDeparturesEndExactlyOnceWithoutRetry() throws Exception {
        stored.set(record(RtcCallState.ACCEPTED, List.of(10L, 20L)));
        var barrier = new CyclicBarrier(2);
        var reads = new AtomicInteger();
        when(repository.findByCallId("call")).thenAnswer(_ -> {
            var snapshot = fixture.read();
            if (reads.incrementAndGet() <= 2) barrier.await(5, TimeUnit.SECONDS);
            return snapshot;
        });
        RtcCallService secondNode = node();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> depart(calls, 10L));
            var second = pool.submit(() -> depart(secondNode, 20L));
            Long failedFirst = first.get(10, TimeUnit.SECONDS);
            Long failedSecond = second.get(10, TimeUnit.SECONDS);
            assertNull(failedFirst);
            assertNull(failedSecond);
            assertEquals(RtcCallState.ENDED, stored.get().state());
            verify(terminal, times(1)).onTerminal(any(), anyLong());
        }
    }

    @Test
    public void concurrentAcceptancePreventsStaleLastDepartureFromEndingCall() {
        stored.set(record(RtcCallState.RINGING, List.of(10L, 20L)));
        RtcCallRecord stale = stored.get();
        node().accept("call", 20L, command());
        when(repository.findByCallId("call")).thenReturn(Optional.of(stale)).thenAnswer(_ -> fixture.read());
        assertEquals(RtcCallState.ACCEPTED, calls.leave("call", 10L, command()).state());
        assertEquals(RtcParticipantState.ACCEPTED, stored.get().participantStates().get(20L));
        verifyNoInteractions(terminal);
    }

    private Long depart(RtcCallService node, Long uid) {
        try {
            node.leave("call", uid, command());
            return null;
        } catch (RtcCallConflictException exception) {
            return uid;
        }
    }

    private RtcCallService node() {
        return new RtcCallService(repository, permission, events::add,
                () -> UUID.randomUUID().toString(), terminal, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private CallActionCommand command() {
        return new CallActionCommand("leave", 1, DEVICE);
    }

    private RtcCallRecord record(RtcCallState state, List<Long> uids) {
        Map<Long, RtcParticipantState> states = new LinkedHashMap<>();
        Map<Long, RtcDevice> devices = new LinkedHashMap<>();
        for (Long uid : uids) {
            boolean accepted = uid.equals(10L) || state != RtcCallState.RINGING;
            states.put(uid, accepted ? RtcParticipantState.ACCEPTED : RtcParticipantState.INVITED);
            if (accepted) devices.put(uid, DEVICE);
        }
        Map<Long, Integer> versions = new LinkedHashMap<>();
        uids.forEach(uid -> versions.put(uid, 1));
        return new RtcCallRecord("call", 100L, 10L, null, RtcMediaMode.VIDEO, state, 1,
                "create", "room:100", NOW.plusSeconds(60), null, NOW, NOW, null,
                state == RtcCallState.RINGING ? null : NOW, null, null, null, null, uids, states, devices, versions);
    }

    private RtcCallRecord copy(RtcCallRecord previous, Map<Long, RtcParticipantState> states,
                               RtcCallState state, String reason, Long actor) {
        return new RtcCallRecord(previous.callId(), previous.roomId(), previous.callerUid(),
                previous.calleeUid(), previous.mediaMode(), state, previous.version() + 1,
                previous.clientRequestId(), state.terminal() ? null : previous.activeKey(),
                previous.expiresAt(), reason, previous.createdAt(), NOW, state.terminal() ? NOW : null,
                previous.acceptedAt(), previous.connectedAt(), null, actor, null,
                previous.participantUids(), states, previous.participantDevices(), previous.participantVersions());
    }
}
