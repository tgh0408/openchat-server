package com.openchat.webrtc.service;

import com.openchat.chat.dao.GroupMemberDao;
import com.openchat.chat.dao.RoomDao;
import com.openchat.chat.dao.RoomGroupDao;
import com.openchat.chat.domain.entity.GroupMember;
import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.entity.RoomGroup;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.service.RoomAccessService;
import com.openchat.common.domain.dto.RequestInfo;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.common.exception.BusinessException;
import com.openchat.common.utils.RequestHolder;
import com.openchat.rtc.domain.*;
import com.openchat.rtc.domain.vo.CallActionCommand;
import com.openchat.rtc.domain.vo.RtcEvent;
import com.openchat.rtc.port.*;
import com.openchat.rtc.service.*;
import com.openchat.user.dao.RoomFriendDao;
import com.openchat.user.dao.UserDao;
import com.openchat.user.dao.UserFriendDao;
import com.openchat.user.domain.entity.User;
import com.openchat.user.domain.enums.IdTypeEnum;
import com.openchat.user.domain.enums.TerminalEnum;
import org.junit.After;
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

/** 真实权限与状态机配合原子存储替身，不连接数据库、Redis、MQ 或 LiveKit。 */
public class RtcGroupJoinTest {
    private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
    private static final RtcDevice PC = new RtcDevice("PC", "a".repeat(64));
    private static final RtcDevice PHONE = new RtcDevice("PHONE", "b".repeat(64));
    private final AtomicReference<RtcCallRecord> stored = new AtomicReference<>();
    private final List<RtcEvent> events = new CopyOnWriteArrayList<>();
    private RtcCallRepository repository;
    private RtcCallTerminalHandler terminal;
    private RtcPermissionPort permission;
    private RtcCallService calls;
    private GroupMemberDao members;
    private Room room;
    private RoomGroup group;
    private GroupCallRepositoryFixture fixture;

    @Before
    public void setUp() {
        repository = mock(RtcCallRepository.class);
        terminal = mock(RtcCallTerminalHandler.class);
        var rooms = mock(RoomDao.class);
        var groups = mock(RoomGroupDao.class);
        members = mock(GroupMemberDao.class);
        room = new Room();
        room.setType(RoomTypeEnum.GROUP);
        group = new RoomGroup();
        group.setDeleteStatus(YesOrNoEnum.NO);
        when(rooms.get(100L)).thenReturn(room);
        when(rooms.get(100L, "房间不存在")).thenReturn(room);
        when(groups.get(100L)).thenReturn(group);
        for (long uid : List.of(10L, 20L, 30L, 40L)) {
            var member = new GroupMember();
            member.setUid(uid);
            member.setDeleteStatus(YesOrNoEnum.NO);
            when(members.getMember(100L, uid)).thenReturn(member);
        }
        var access = new RoomAccessService(rooms, groups, members, mock(RoomFriendDao.class),
                mock(UserDao.class), mock(UserFriendDao.class));
        permission = new RtcPermissionService(access, members);
        calls = node();
        stored.set(record(RtcCallState.RINGING, RtcParticipantState.INVITED, NOW.plusSeconds(60)));
        fixture = new GroupCallRepositoryFixture(repository, stored);
        when(repository.findByCallId("call")).thenAnswer(_ -> fixture.read());
        when(repository.updateParticipants(any(), anyMap(), any(), nullable(String.class),
                nullable(Long.class), any(), anyBoolean(), anyBoolean())).thenAnswer(invocation -> {
            RtcCallRecord previous = invocation.getArgument(0);
            RtcCallRecord current = stored.get();
            if (!previous.version().equals(current.version()) || previous.state() != current.state()
                    || current.state().terminal()) return false;
            boolean accepting = invocation.getArgument(6);
            boolean expiring = invocation.getArgument(7);
            if (accepting && !current.expiresAt().isAfter(NOW)) return false;
            if (expiring && current.expiresAt().isAfter(NOW)) return false;
            RtcCallState state = invocation.getArgument(2);
            var updated = new RtcCallRecord(previous.callId(), previous.roomId(), previous.callerUid(),
                    previous.calleeUid(), previous.mediaMode(), state, previous.version() + 1,
                    previous.clientRequestId(), state.terminal() ? null : previous.activeKey(),
                    previous.expiresAt(), invocation.getArgument(3), previous.createdAt(), NOW,
                    state.terminal() ? NOW : null,
                    state == RtcCallState.ACCEPTED && previous.acceptedAt() == null ? NOW : previous.acceptedAt(),
                    previous.connectedAt(), null, invocation.getArgument(4), null,
                    previous.participantUids(), invocation.getArgument(1), previous.participantDevices());
            return stored.compareAndSet(current, updated);
        });
    }

    @After
    public void cleanup() {
        RequestHolder.remove();
    }

    @Test
    public void uninvitedMemberCanReadAndJoinEveryActiveGroupState() {
        for (var state : List.of(RtcCallState.RINGING, RtcCallState.ACCEPTED, RtcCallState.CONNECTED)) {
            stored.set(record(state, RtcParticipantState.INVITED, NOW.plusSeconds(60)));
            assertEquals(2, calls.get("call", 30L).participants().size());
            var joined = calls.accept("call", 30L, action(PC));
            assertEquals(state == RtcCallState.RINGING ? RtcCallState.ACCEPTED : state, joined.state());
            assertEquals(List.of(10L, 20L, 30L), stored.get().participantUids());
            assertEquals(RtcParticipantState.ACCEPTED, stored.get().participantStates().get(30L));
            assertEquals(PC, stored.get().participantDevices().get(30L));
            assertEquals(RtcParticipantState.INVITED, stored.get().participantStates().get(20L));
        }
        assertTrue(events.stream().anyMatch(event -> event.toUid().equals(30L)
                && event.eventType() == RtcEventType.RTC_CALL_PARTICIPANT_ACCEPTED));
        verifyNoInteractions(terminal);
    }

    @Test
    public void invitationDeadlineAndPreviousResponseDoNotPreventGroupJoin() {
        for (var state : List.of(RtcParticipantState.INVITED, RtcParticipantState.REJECTED,
                RtcParticipantState.EXPIRED)) {
            stored.set(record(RtcCallState.RINGING, state, NOW.minusSeconds(60)));
            assertEquals(RtcCallState.ACCEPTED, calls.accept("call", 20L, action(PC)).state());
            assertEquals(RtcParticipantState.ACCEPTED, stored.get().participantStates().get(20L));
        }
        stored.set(record(RtcCallState.RINGING, RtcParticipantState.EXPIRED, NOW.minusSeconds(60)));
        assertEquals(RtcCallState.ACCEPTED, calls.accept("call", 30L, action(PC)).state());
        verify(repository, never()).updateParticipants(any(), anyMap(), any(), any(), any(), any(), eq(true), anyBoolean());
    }

    @Test
    public void duplicateJoinKeepsVersionAndOtherDeviceCannotTakeOwnership() {
        var joined = calls.accept("call", 30L, action(PC));
        int count = events.size();
        assertEquals(joined.version(), calls.accept("call", 30L, action(PC)).version());
        assertEquals(count, events.size());
        assertEquals(RtcOperationException.Reason.ACCEPTED_ELSEWHERE,
                assertThrows(RtcOperationException.class, () -> calls.accept("call", 30L, action(PHONE))).reason());
        assertEquals(PC, stored.get().participantDevices().get(30L));
    }

    @Test
    public void staleAcceptCannotUndoLeaveButFreshJoinCanBindNewDevice() {
        calls.accept("call", 30L, action(PC));
        calls.leave("call", 30L, action(PC));
        assertThrows(RtcCallConflictException.class, () -> calls.accept("call", 30L, action(PC)));
        var fresh = new CallActionCommand("rejoin", stored.get().participantVersions().get(30L), PHONE);
        calls.accept("call", 30L, fresh);
        assertEquals(PHONE, stored.get().participantDevices().get(30L));
        assertEquals(3, stored.get().participantUids().size());
    }

    @Test
    public void callerCanRejoinWhileAnotherMemberRemains() {
        calls.accept("call", 20L, action(PC));
        calls.leave("call", 10L, action(PC));
        calls.accept("call", 10L, new CallActionCommand("rejoin", stored.get().participantVersions().get(10L), PHONE));
        assertEquals(RtcParticipantState.ACCEPTED, stored.get().participantStates().get(10L));
        assertEquals(PHONE, stored.get().participantDevices().get(10L));
        verifyNoInteractions(terminal);
    }

    @Test
    public void outsiderRemovedMemberAndDissolvedGroupCannotReadOrJoin() {
        assertDenied(99L);
        when(members.getMember(100L, 30L)).thenReturn(null);
        assertDenied(30L);
        var deleted = new GroupMember();
        deleted.setDeleteStatus(YesOrNoEnum.YES);
        when(members.getMember(100L, 20L)).thenReturn(deleted);
        assertDenied(20L);
        group.setDeleteStatus(YesOrNoEnum.YES);
        assertDenied(40L);
        assertTrue(events.isEmpty());
        verify(repository, never()).updateParticipants(any(), anyMap(), any(), any(), any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    public void endedCallAndUnknownDeviceCannotBeJoined() {
        assertEquals(RtcOperationException.Reason.DEVICE_UNKNOWN,
                assertThrows(RtcOperationException.class, () -> calls.accept("call", 30L, action(null))).reason());
        for (var state : List.of(RtcCallState.ENDED, RtcCallState.CANCELED, RtcCallState.REJECTED, RtcCallState.EXPIRED)) {
            stored.set(record(state, RtcParticipantState.EXPIRED, NOW.minusSeconds(60)));
            assertEquals(RtcOperationException.Reason.CALL_UNAVAILABLE,
                    assertThrows(RtcOperationException.class, () -> calls.accept("call", 30L, action(PC))).reason());
        }
        assertTrue(events.isEmpty());
    }

    @Test
    public void singleCallStillRequiresInvitationAndDeadline() {
        room.setType(RoomTypeEnum.FRIEND);
        stored.set(record(RtcCallState.RINGING, RtcParticipantState.INVITED, NOW.plusSeconds(60)));
        assertDenied(30L);
        assertThrows(IllegalStateException.class, () -> calls.accept("call", 10L, action(PC)));
        assertEquals(RtcCallState.ACCEPTED, calls.accept("call", 20L, action(PC)).state());
        stored.set(record(RtcCallState.RINGING, RtcParticipantState.INVITED, NOW.minusSeconds(1)));
        assertEquals(RtcOperationException.Reason.CALL_UNAVAILABLE,
                assertThrows(RtcOperationException.class, () -> calls.accept("call", 20L, action(PC))).reason());
        stored.set(record(RtcCallState.REJECTED, RtcParticipantState.REJECTED, NOW.plusSeconds(60)));
        assertThrows(RtcOperationException.class, () -> calls.accept("call", 20L, action(PC)));
    }

    @Test
    public void tokenRequiresSuccessfulJoinAndStillChecksCurrentMembership() {
        var identity = new RequestInfo();
        identity.setUid(30L);
        identity.setTerminal(TerminalEnum.PC);
        identity.setDeviceId(PC.deviceId());
        RequestHolder.set(identity);
        var users = mock(UserDao.class);
        var user = new User();
        user.setName("member");
        when(users.get(IdTypeEnum.ID, 30L, "用户不存在")).thenReturn(user);
        var tokens = mock(LiveKitTokenService.class);
        var liveKit = new RtcLiveKitService(calls, users, tokens, mock(RtcVideoSeatService.class));
        assertThrows(RtcOperationException.class, () -> liveKit.issueToken("call", 30L));
        verifyNoInteractions(tokens);
        calls.accept("call", 30L, action(PC));
        liveKit.issueToken("call", 30L);
        verify(tokens).issue(anyString(), eq("30"), eq("member"), eq(false));
        when(members.getMember(100L, 30L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> liveKit.issueToken("call", 30L));
        verifyNoMoreInteractions(tokens);
    }

    @Test
    public void concurrentDifferentMembersJoinWithoutAggregateVersionConflict() throws Exception {
        var barrier = new CyclicBarrier(2);
        var reads = new AtomicInteger();
        when(repository.findByCallId("call")).thenAnswer(_ -> {
            var snapshot = fixture.read();
            if (reads.incrementAndGet() <= 2) barrier.await(5, TimeUnit.SECONDS);
            return snapshot;
        });
        var secondNode = node();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> joinOutcome(calls, 30L));
            var second = pool.submit(() -> joinOutcome(secondNode, 40L));
            boolean firstWon = first.get(10, TimeUnit.SECONDS);
            boolean secondWon = second.get(10, TimeUnit.SECONDS);
            assertTrue(firstWon && secondWon);
            assertEquals(Integer.valueOf(3), stored.get().version());
            assertEquals(4, stored.get().participantUids().size());
            assertEquals(2, events.stream().filter(event -> event.eventType()
                    == RtcEventType.RTC_CALL_ANSWERED_ELSEWHERE).count());
            assertEquals(Set.of(10L, 20L, 30L, 40L), new HashSet<>(stored.get().participantUids()));
        }
    }

    @Test
    public void joinPreventsStaleLastDepartureFromEndingCall() {
        var stale = stored.get();
        node().accept("call", 30L, action(PC));
        when(repository.findByCallId("call")).thenReturn(Optional.of(stale)).thenAnswer(_ -> fixture.read());
        assertEquals(RtcCallState.ACCEPTED, calls.leave("call", 10L, action(PC)).state());
        assertEquals(RtcParticipantState.LEFT, stored.get().participantStates().get(10L));
        assertEquals(RtcCallState.ACCEPTED, stored.get().state());
        verifyNoInteractions(terminal);
    }

    @Test
    public void lastDeparturePreventsStaleJoinFromRevivingCall() {
        var stale = stored.get();
        node().leave("call", 10L, action(PC));
        int count = events.size();
        when(repository.findByCallId("call")).thenReturn(Optional.of(stale));
        assertThrows(RtcCallConflictException.class, () -> calls.accept("call", 30L, action(PC)));
        assertEquals(RtcCallState.CANCELED, stored.get().state());
        assertFalse(stored.get().participantUids().contains(30L));
        assertEquals(count, events.size());
        verify(terminal).onTerminal(any(), eq(10L));
    }

    private void assertDenied(Long uid) {
        assertThrows(BusinessException.class, () -> calls.get("call", uid));
        assertThrows(BusinessException.class, () -> calls.accept("call", uid, action(PC)));
    }

    private boolean joinOutcome(RtcCallService node, Long uid) {
        try {
            node.accept("call", uid, action(PC));
            return true;
        } catch (RtcCallConflictException exception) {
            return false;
        }
    }

    private RtcCallService node() {
        return new RtcCallService(repository, permission, events::add, () -> UUID.randomUUID().toString(),
                terminal, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private CallActionCommand action(RtcDevice device) {
        return new CallActionCommand("join", 1, device);
    }

    private RtcCallRecord record(RtcCallState state, RtcParticipantState peerState, Instant expiresAt) {
        return new RtcCallRecord("call", 100L, 10L, room.getType() == RoomTypeEnum.GROUP ? null : 20L, RtcMediaMode.AUDIO, state, 1,
                "create", state.terminal() ? null : "room:100", expiresAt, null, NOW, NOW, null,
                state == RtcCallState.RINGING ? null : NOW, null, null, null, null,
                List.of(10L, 20L), Map.of(10L, RtcParticipantState.ACCEPTED, 20L, peerState), Map.of(10L, PC),
                Map.of(10L, 1, 20L, 1));
    }
}
