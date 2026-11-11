package com.openchat.webrtc.service;

import com.openchat.rtc.domain.*;
import com.openchat.rtc.domain.vo.RtcEvent;
import com.openchat.rtc.port.*;
import com.openchat.rtc.service.RtcCallConflictException;
import com.openchat.webrtc.dao.*;
import com.openchat.webrtc.domain.entity.RtcMediaSession;
import com.openchat.webrtc.domain.entity.RtcWebhookEvent;
import com.openchat.webrtc.domain.enums.RtcMediaTransportEnum;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** 使用真实回调状态处理器及持久化替身验证媒体时间、终态和并发冲突边界。 */
public class RtcWebhookCallServiceTest {
    private static final long NOW = 1794182400L;
    private final RtcCallDao calls = mock(RtcCallDao.class);
    private final RtcMediaDao media = mock(RtcMediaDao.class);
    private final RtcWebhookEventDao events = mock(RtcWebhookEventDao.class);
    private final RtcMediaCleanupService liveKit = mock(RtcMediaCleanupService.class);
    private final RtcCallTerminalHandler terminal = mock(RtcCallTerminalHandler.class);
    private final RtcEventPublisher publisher = mock(RtcEventPublisher.class);
    private final RtcIdGenerator ids = mock(RtcIdGenerator.class);
    private final RtcWebhookCallService service =
            new RtcWebhookCallService(calls, media, events, liveKit, terminal, publisher, ids);
    private RtcCallRecord call;

    @Before
    public void setUp() {
        call = call(RtcCallState.ACCEPTED, null, false);
        var session = new RtcMediaSession();
        session.setTransport(RtcMediaTransportEnum.SFU);
        when(media.selectById("call")).thenReturn(session);
        when(calls.claimWebhook(any())).thenReturn(true);
        when(calls.findByCallId("call")).thenReturn(Optional.of(call));
        when(calls.updateFromWebhook(any(), nullable(Instant.class), nullable(Instant.class))).thenReturn(true);
        when(events.listRoomEvents("call", "RM_1")).thenReturn(List.of());
        when(liveKit.isRoomClosed("call", "RM_1")).thenReturn(true);
        when(ids.nextId()).thenReturn("notification");
    }

    @Test
    public void twoDistinctMembersConnectAtTheLaterJoinTime() {
        var first = event("participant_joined", "PA_1", 10L, NOW - 30);
        var second = event("participant_joined", "PA_2", 20L, NOW - 20);
        when(events.listRoomEvents("call", "RM_1")).thenReturn(List.of(second, first));
        service.apply(call, second);
        verify(calls).updateFromWebhook(call, Instant.ofEpochSecond(NOW - 20), null);
        var order = inOrder(calls, events);
        order.verify(calls).claimWebhook(call);
        order.verify(calls).findByCallId("call");
        order.verify(events).listRoomEvents("call", "RM_1");
        verifyNoInteractions(terminal, publisher, liveKit);
    }

    @Test
    public void oneMemberOrTwoSessionsOfTheSameMemberDoNotConnectCall() {
        var first = event("participant_joined", "PA_1", 10L, NOW - 30);
        var second = event("participant_joined", "PA_2", 10L, NOW - 20);
        when(events.listRoomEvents("call", "RM_1")).thenReturn(List.of(first));
        service.apply(call, first);
        when(events.listRoomEvents("call", "RM_1")).thenReturn(List.of(first, second));
        service.apply(call, second);
        verify(calls, never()).updateFromWebhook(any(), any(), any());
    }

    @Test
    public void outOfOrderLeavesAndSameSecondBoundariesDoNotInventOverlap() {
        var join = event("participant_joined", "PA_1", 10L, NOW - 30);
        var leave = event("participant_left", "PA_1", 10L, NOW - 20);
        var peer = event("participant_joined", "PA_2", 20L, NOW - 20);
        when(events.listRoomEvents("call", "RM_1")).thenReturn(List.of(leave, peer, join));
        service.apply(call, join);
        verify(calls, never()).updateFromWebhook(any(), any(), any());
        verifyNoInteractions(terminal, publisher);
    }

    @Test
    public void oldSessionLeaveDoesNotCloseNewSessionOfSameUid() {
        var oldJoin = event("participant_joined", "OLD", 10L, NOW - 40);
        var oldLeave = event("participant_left", "OLD", 10L, NOW - 30);
        var newJoin = event("participant_joined", "NEW", 10L, NOW - 20);
        var peer = event("participant_joined", "PEER", 20L, NOW - 10);
        when(events.listRoomEvents("call", "RM_1")).thenReturn(List.of(oldLeave, peer, oldJoin, newJoin));
        service.apply(call, oldLeave);
        verify(calls).updateFromWebhook(call, Instant.ofEpochSecond(NOW - 10), null);
    }

    @Test
    public void connectionNeverMovesBeforeAcceptanceWithMillisecondPrecision() {
        var first = event("participant_joined", "PA_1", 10L, NOW - 60);
        var second = event("participant_joined", "PA_2", 20L, NOW - 60);
        when(events.listRoomEvents("call", "RM_1")).thenReturn(List.of(first, second));
        service.apply(call, second);
        verify(calls).updateFromWebhook(call, call.acceptedAt(), null);
    }

    @Test
    public void confirmedConnectionDoesNotResetOnReconnectOrParticipantDeparture() {
        call = call(RtcCallState.CONNECTED, Instant.ofEpochSecond(NOW - 50), false);
        when(calls.findByCallId("call")).thenReturn(Optional.of(call));
        service.apply(call, event("participant_left", "PA_1", 10L, NOW));
        verify(calls, never()).updateFromWebhook(any(), any(), any());
        verify(events, never()).listRoomEvents(anyString(), anyString());
        verifyNoInteractions(terminal, publisher, liveKit);
    }

    @Test
    public void closedRoomEndsCallAtEventTimeAndPublishesExistingTerminalFlow() {
        call = call(RtcCallState.CONNECTED, Instant.ofEpochSecond(NOW - 50), false);
        var ended = call(RtcCallState.ENDED, call.connectedAt(), false);
        when(calls.findByCallId("call")).thenReturn(Optional.of(call), Optional.of(ended));
        service.apply(call, event("room_finished", null, null, NOW));
        verify(calls).updateFromWebhook(call, call.connectedAt(), Instant.ofEpochSecond(NOW));
        verify(terminal).onTerminal(ended, null);
        var captured = ArgumentCaptor.forClass(RtcEvent.class);
        verify(publisher, times(2)).publish(captured.capture());
        assertEquals(List.of(10L, 20L), captured.getAllValues().stream().map(RtcEvent::toUid).toList());
        assertTrue(captured.getAllValues().stream().allMatch(e ->
                e.eventType() == RtcEventType.RTC_CALL_ENDED && e.fromUid() == null));
        var order = inOrder(liveKit, calls, terminal);
        order.verify(liveKit).isRoomClosed("call", "RM_1");
        order.verify(calls).claimWebhook(call);
        order.verify(calls).findByCallId("call");
        order.verify(calls).updateFromWebhook(any(), any(), any());
        order.verify(calls).findByCallId("call");
        order.verify(terminal).onTerminal(ended, null);
    }

    @Test
    public void groupRoomFinishUsesSameTerminalFlowForAllMembers() {
        call = call(RtcCallState.CONNECTED, Instant.ofEpochSecond(NOW - 50), true);
        var ended = call(RtcCallState.ENDED, call.connectedAt(), true);
        when(calls.findByCallId("call")).thenReturn(Optional.of(call), Optional.of(ended));
        service.apply(call, event("room_finished", null, null, NOW));
        verify(calls).updateFromWebhook(call, call.connectedAt(), Instant.ofEpochSecond(NOW));
        verify(terminal).onTerminal(ended, null);
        verify(publisher, times(3)).publish(any());
    }

    @Test
    public void finishedEventCanSetKnownConnectionAndTerminalTogether() {
        var first = event("participant_joined", "PA_1", 10L, NOW - 30);
        var second = event("participant_joined", "PA_2", 20L, NOW - 20);
        when(events.listRoomEvents("call", "RM_1")).thenReturn(List.of(first, second));
        var ended = call(RtcCallState.ENDED, Instant.ofEpochSecond(NOW - 20), false);
        when(calls.findByCallId("call")).thenReturn(Optional.of(call), Optional.of(ended));
        service.apply(call, event("room_finished", null, null, NOW));
        verify(calls).updateFromWebhook(call, ended.connectedAt(), Instant.ofEpochSecond(NOW));
    }

    @Test
    public void latestRoomEvidenceOrLiveRoomPreventsOldFinish() {
        var finished = event("room_finished", null, null, NOW);
        when(events.hasNewerRoom(finished)).thenReturn(true);
        service.apply(call, finished);
        verifyNoInteractions(liveKit);
        when(events.hasNewerRoom(finished)).thenReturn(false);
        when(liveKit.isRoomClosed("call", "RM_1")).thenReturn(false);
        service.apply(call, finished);
        verify(calls, never()).updateFromWebhook(any(), any(), any());
        verifyNoInteractions(terminal, publisher);
    }

    @Test
    public void roomQueryFailureIsRetryableAndDoesNotClaimCall() {
        when(liveKit.isRoomClosed("call", "RM_1")).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class,
                () -> service.apply(call, event("room_finished", null, null, NOW)));
        verify(calls, never()).claimWebhook(any());
        verifyNoInteractions(terminal, publisher);
    }

    @Test
    public void terminalRingingAndP2pCallsRemainUnchanged() {
        service.apply(call(RtcCallState.ENDED, null, false), event("participant_joined", "PA_1", 10L, NOW));
        service.apply(call(RtcCallState.RINGING, null, false), event("room_started", null, null, NOW));
        var direct = new RtcMediaSession();
        direct.setTransport(RtcMediaTransportEnum.P2P);
        when(media.selectById("call")).thenReturn(direct);
        service.apply(call, event("room_finished", null, null, NOW));
        verifyNoInteractions(calls, events, liveKit, terminal, publisher);
    }

    @Test
    public void optimisticConflictFailsWithoutRetryOrTerminalSideEffects() {
        when(calls.claimWebhook(call)).thenReturn(false);
        assertThrows(RtcCallConflictException.class,
                () -> service.apply(call, event("participant_joined", "PA_1", 10L, NOW)));
        verify(calls, times(1)).claimWebhook(call);
        verify(calls, never()).findByCallId(anyString());
        verifyNoInteractions(events, terminal, publisher);
    }

    @Test
    public void failedFinalWriteDoesNotPublishSummaryOrNotifications() {
        when(calls.updateFromWebhook(any(), nullable(Instant.class), nullable(Instant.class))).thenReturn(false);
        assertThrows(RtcCallConflictException.class,
                () -> service.apply(call, event("room_finished", null, null, NOW)));
        verify(calls, times(1)).findByCallId("call");
        verifyNoInteractions(terminal, publisher);
    }

    private RtcCallRecord call(RtcCallState state, Instant connectedAt, boolean group) {
        Instant accepted = Instant.ofEpochSecond(NOW - 60).plusMillis(100);
        List<Long> uids = group ? List.of(10L, 20L, 30L) : List.of(10L, 20L);
        Map<Long, RtcParticipantState> states = group ? Map.of(10L, RtcParticipantState.ACCEPTED,
                20L, RtcParticipantState.ACCEPTED, 30L, RtcParticipantState.INVITED) : null;
        return new RtcCallRecord("call", 100L, 10L, group ? null : 20L,
                RtcMediaMode.AUDIO, state, 3, "request", state.terminal() ? null : "room:100", null,
                state.terminal() ? RtcEndReason.RTC_CALL_ENDED.getValue() : null,
                accepted.minusSeconds(10), accepted, state.terminal() ? Instant.ofEpochSecond(NOW) : null,
                accepted, connectedAt, state.terminal() ? 50L : null, null, null,
                uids, states, Map.of(), Map.of());
    }

    private RtcWebhookEvent event(String type, String participantSid, Long uid, long time) {
        var event = new RtcWebhookEvent();
        event.setCallId("call");
        event.setEventType(type);
        event.setRoomSid("RM_1");
        event.setRoomCreatedAtSeconds(NOW - 70);
        event.setParticipantSid(participantSid);
        event.setUid(uid);
        event.setOccurredAtSeconds(time);
        return event;
    }
}
