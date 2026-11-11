package com.openchat.webrtc.service;

import com.openchat.chat.dao.RoomDao;
import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.common.exception.BusinessException;
import com.openchat.rtc.domain.*;
import com.openchat.rtc.domain.vo.CallActionCommand;
import com.openchat.rtc.domain.vo.CreateCallCommand;
import com.openchat.rtc.domain.vo.RtcCallView;
import com.openchat.rtc.port.RtcCallParticipants;
import com.openchat.rtc.port.RtcEventPublisher;
import com.openchat.rtc.service.RtcCallConflictException;
import com.openchat.rtc.service.RtcCallService;
import com.openchat.webrtc.dao.RtcCallDao;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证群房间发现与创建转加入，依赖替身不连接运行中的服务。 */
public class RtcGroupDiscoveryTest {
    private final RtcCallDao repository = mock(RtcCallDao.class);
    private final RtcPermissionService permission = mock(RtcPermissionService.class);
    private final RoomDao rooms = mock(RoomDao.class);
    private final RtcCallService calls = mock(RtcCallService.class);
    private final RtcDialService dial = new RtcDialService(repository, calls, permission, rooms);
    private final RtcDevice device = new RtcDevice("PC", "a".repeat(64));
    private final CreateCallCommand command =
            new CreateCallCommand(100L, RtcMediaMode.VIDEO, "join-request", List.of(40L), device);
    private RtcCallRecord existing;
    private RtcCallView view;

    @Before
    public void setUp() {
        var room = new Room();
        room.setType(RoomTypeEnum.GROUP);
        when(rooms.get(100L)).thenReturn(room);
        Instant now = Instant.now();
        existing = new RtcCallRecord("group", 100L, 10L, 20L, RtcMediaMode.AUDIO,
                RtcCallState.CONNECTED, 7, "original", "room:100", now.minusSeconds(60),
                null, now, now, null);
        existing = GroupCallRepositoryFixture.group(existing);
        // 使用真实状态机组装快照，保留原发起人、媒体类型和参与者。
        var reader = new RtcCallService(repository, permission, mock(RtcEventPublisher.class), () -> "event");
        when(repository.findByCallId("group")).thenReturn(Optional.of(existing));
        view = reader.get("group", 30L);
        when(repository.findByActiveKey("room:100")).thenReturn(Optional.of(existing));
        when(repository.findActiveByParticipant(30L)).thenReturn(List.of());
        when(calls.get("group", 30L)).thenReturn(view);
        when(calls.accept(eq("group"), eq(30L), any())).thenReturn(view);
        clearInvocations(permission, repository);
    }

    @Test
    public void discoveryDoesNotRequireAnInvitationOrWriteState() {
        assertSame(view, dial.activeGroup(30L, 100L));
        verify(permission).requireGroupMember(30L, 100L);
        verify(repository, never()).findActiveByParticipant(anyLong());
        verify(calls, never()).accept(anyString(), anyLong(), any());
        verify(calls, never()).create(anyLong(), any());
    }

    @Test
    public void emptyRoomReturnsNullAndPermissionFailureIsNotAnEmptyResult() {
        when(repository.findByActiveKey("room:100")).thenReturn(Optional.empty());
        assertNull(dial.activeGroup(30L, 100L));
        clearInvocations(repository);
        doThrow(new BusinessException("不在群中")).when(permission).requireGroupMember(30L, 100L);
        assertThrows(BusinessException.class, () -> dial.activeGroup(30L, 100L));
        verifyNoInteractions(repository);
    }

    @Test
    public void createJoinsExistingGroupWithoutRevalidatingOrInvitingSelectedMembers() {
        assertSame(view, dial.create(30L, command, List.of()));
        verify(calls).accept("group", 30L, new CallActionCommand("join-request", 1L, device));
        verify(permission, never()).requireCreate(anyLong(), any());
        verify(calls, never()).create(anyLong(), any());
        verify(calls, never()).endForRedial(anyString(), anyLong(), anyBoolean());
    }

    @Test
    public void sameGroupParticipationIsNeverReplacedEvenWhenExplicitlyListed() {
        when(repository.findActiveByParticipant(30L)).thenReturn(List.of(existing));
        dial.create(30L, command, List.of("group"));
        verify(calls, never()).endForRedial(anyString(), anyLong(), anyBoolean());
        verify(calls).accept(eq("group"), eq(30L), any());
    }

    @Test
    public void otherUnconfirmedCallPreventsJoining() {
        var other = new RtcCallRecord("other", 200L, 30L, 40L, RtcMediaMode.AUDIO,
                RtcCallState.RINGING, 1, "other-request", "room:200", Instant.now().plusSeconds(60),
                null, Instant.now(), Instant.now(), null);
        when(repository.findActiveByParticipant(30L)).thenReturn(List.of(other));
        assertThrows(BusinessException.class, () -> dial.create(30L, command, List.of()));
        verify(calls, never()).accept(anyString(), anyLong(), any());
    }

    @Test
    public void concurrentCreateWinnerIsJoinedWithoutReplacingItsCallIdOrMedia() {
        when(repository.findByActiveKey("room:100")).thenReturn(Optional.empty());
        when(calls.create(30L, command)).thenReturn(view);
        assertSame(view, dial.create(30L, command, List.of()));
        verify(calls).accept("group", 30L, new CallActionCommand("join-request", 1L, device));
        assertEquals(RtcMediaMode.AUDIO, view.mediaMode());
    }

    @Test
    public void joinConflictPropagatesWithoutRetry() {
        when(calls.accept(anyString(), anyLong(), any())).thenThrow(new RtcCallConflictException());
        assertThrows(RtcCallConflictException.class, () -> dial.create(30L, command, List.of()));
        verify(calls).accept(anyString(), anyLong(), any());
    }

    @Test
    public void stateMachineAllowsUninvitedGroupMemberToReadConcurrentCreateWinner() {
        when(permission.requireCreate(30L, command))
                .thenReturn(new RtcCallParticipants(100L, 30L, 40L));
        when(permission.isGroup(existing)).thenReturn(true);
        when(repository.insert(any())).thenReturn(false);
        var service = new RtcCallService(repository, permission, mock(RtcEventPublisher.class), () -> "candidate");
        assertEquals("group", service.create(30L, command).callId());
        verify(permission).requireJoin(existing, 30L);
        verify(permission, never()).requireParticipant(existing, 30L);
    }
}
