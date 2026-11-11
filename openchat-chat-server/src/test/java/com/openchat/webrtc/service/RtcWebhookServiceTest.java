package com.openchat.webrtc.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.openchat.rtc.domain.RtcCallRecord;
import com.openchat.rtc.domain.RtcCallState;
import com.openchat.rtc.domain.RtcMediaMode;
import com.openchat.rtc.port.RtcCallRepository;
import com.openchat.webrtc.dao.RtcWebhookEventDao;
import com.openchat.webrtc.domain.entity.RtcWebhookEvent;
import livekit.LivekitModels.ParticipantInfo;
import livekit.LivekitModels.Room;
import livekit.LivekitWebhook.WebhookEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** 媒体事件落库、时间语义和乱序回归，不连接数据库或 LiveKit。 */
public class RtcWebhookServiceTest {
    private static final long EVENT_TIME = 1794182400L;
    private RtcCallRepository calls;
    private RtcWebhookEventDao events;
    private RtcWebhookCallService callUpdates;
    private RtcWebhookService service;
    private Logger logger;
    private Level previousLevel;
    private ListAppender<ILoggingEvent> appender;

    @Before
    public void setUp() {
        calls = mock(RtcCallRepository.class);
        events = mock(RtcWebhookEventDao.class);
        callUpdates = mock(RtcWebhookCallService.class);
        when(events.insertIfAbsent(any())).thenReturn(true);
        service = new RtcWebhookService(calls, events, callUpdates);
        Instant end = Instant.ofEpochSecond(EVENT_TIME - 10);
        when(calls.findByCallId("call-1")).thenReturn(Optional.of(new RtcCallRecord(
                "call-1", 100L, 10L, 20L, RtcMediaMode.AUDIO, RtcCallState.ENDED, 3,
                "request-1", null, null, "ended", end.minusSeconds(100), end, end,
                end.minusSeconds(70), end.minusSeconds(60), 60L, 10L, null)));
        logger = (Logger) LoggerFactory.getLogger(RtcWebhookService.class);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @After
    public void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
        appender.stop();
    }

    @Test
    public void connectionUsesEventTimeRatherThanDeliveryOrParticipantJoinField() {
        Instant before = Instant.now();
        service.handle(event("participant_joined").setParticipant(ParticipantInfo.newBuilder()
                .setSid("PA_1").setIdentity("10").setJoinedAt(EVENT_TIME - 5)).build());
        var captured = ArgumentCaptor.forClass(RtcWebhookEvent.class);
        verify(events).insertIfAbsent(captured.capture());
        RtcWebhookEvent stored = captured.getValue();
        assertEquals("event-1", stored.getEventId());
        assertEquals("call-1", stored.getCallId());
        assertEquals("participant_joined", stored.getEventType());
        assertEquals("RM_1", stored.getRoomSid());
        assertEquals("PA_1", stored.getParticipantSid());
        assertEquals(Long.valueOf(10), stored.getUid());
        assertEquals(Long.valueOf(EVENT_TIME), stored.getOccurredAtSeconds());
        assertEquals(Long.valueOf(EVENT_TIME - 100), stored.getRoomCreatedAtSeconds());
        assertFalse(stored.getReceivedAt().before(Date.from(before)));
        assertFalse(stored.getReceivedAt().after(new Date()));
        String message = lastMessage();
        assertTrue(message.contains("mediaConnectedAt=" + Instant.ofEpochSecond(EVENT_TIME)));
        assertTrue(message.contains("participantSid=PA_1"));
        assertTrue(message.contains("receivedAt="));
    }

    @Test
    public void roomLifetimeIsSeparateFromBusinessDuration() {
        service.handle(event("room_finished").build());
        String message = lastMessage();
        assertTrue(message.contains("roomLifetimeSeconds=100"));
        assertTrue(message.contains("durationSeconds=60"));
        assertTrue(message.contains("roomFinishedAt=" + Instant.ofEpochSecond(EVENT_TIME)));
        assertTrue(message.contains("endedAt=" + Instant.ofEpochSecond(EVENT_TIME - 10)));
        verify(calls).findByCallId("call-1");
        verifyNoMoreInteractions(calls);
        var captured = ArgumentCaptor.forClass(RtcWebhookEvent.class);
        verify(events).insertIfAbsent(captured.capture());
        assertEquals("room_finished", captured.getValue().getEventType());
        assertNull(captured.getValue().getUid());
        assertNull(captured.getValue().getParticipantSid());
    }

    @Test
    public void missingOrInvalidRoomCreationDoesNotInventDuration() {
        for (long created : new long[]{0, EVENT_TIME + 1}) {
            service.handle(event("room_finished").setRoom(room().setCreationTime(created)).build());
            assertTrue(lastMessage().contains("roomLifetimeSeconds=null"));
        }
        var captured = ArgumentCaptor.forClass(RtcWebhookEvent.class);
        verify(events, times(2)).insertIfAbsent(captured.capture());
        assertTrue(captured.getAllValues().stream().allMatch(row -> row.getRoomCreatedAtSeconds() == null));
    }

    @Test
    public void repeatedAndLateEventsAcrossHandlersPreserveBusinessStateAndOriginalTimes() {
        var otherNode = new RtcWebhookService(calls, events, callUpdates);
        when(events.insertIfAbsent(any())).thenReturn(true, false, true, true);
        var finished = event("room_finished").build();
        service.handle(finished);
        otherNode.handle(finished);
        otherNode.handle(event("participant_left").setId("event-2").build());
        service.handle(event("participant_joined").setId("event-3").setCreatedAt(EVENT_TIME - 30).build());
        assertEquals(3, appender.list.size());
        verify(calls, times(4)).findByCallId("call-1");
        verifyNoMoreInteractions(calls);
        var captured = ArgumentCaptor.forClass(RtcWebhookEvent.class);
        verify(events, times(4)).insertIfAbsent(captured.capture());
        verify(callUpdates, times(3)).apply(any(), any());
        assertEquals("participant_left", captured.getAllValues().get(2).getEventType());
        assertEquals(Long.valueOf(EVENT_TIME - 30), captured.getAllValues().get(3).getOccurredAtSeconds());
    }

    @Test
    public void rebuiltRoomsAndReconnectedParticipantsKeepTheirSessionIdentifiers() {
        service.handle(event("room_started").build());
        service.handle(event("participant_joined").setId("event-2")
                .setRoom(room().setSid("RM_2"))
                .setParticipant(ParticipantInfo.newBuilder().setSid("PA_2").setIdentity("10")).build());
        service.handle(event("participant_left").setId("event-3").build());
        var captured = ArgumentCaptor.forClass(RtcWebhookEvent.class);
        verify(events, times(3)).insertIfAbsent(captured.capture());
        assertNull(captured.getAllValues().get(0).getParticipantSid());
        assertEquals("RM_2", captured.getAllValues().get(1).getRoomSid());
        assertEquals("PA_2", captured.getAllValues().get(1).getParticipantSid());
        assertEquals("RM_1", captured.getAllValues().get(2).getRoomSid());
        assertEquals("PA_1", captured.getAllValues().get(2).getParticipantSid());
    }

    @Test
    public void failedStoragePropagatesWithoutLoggingSuccessAndAllowsRedelivery() {
        var failure = new IllegalStateException("database unavailable");
        when(events.insertIfAbsent(any())).thenThrow(failure).thenReturn(true);
        var joined = event("participant_joined").build();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.handle(joined)));
        assertTrue(appender.list.isEmpty());
        service.handle(joined);
        verify(events, times(2)).insertIfAbsent(any());
        assertEquals(1, appender.list.size());
    }

    @Test
    public void ignoresUnrelatedRoomsAndUnknownEventsWithoutDatabaseAccess() {
        service.handle(event("future_event").build());
        service.handle(event("room_finished").setRoom(room().setName("another-room")).build());
        verifyNoInteractions(calls, events);
        assertTrue(appender.list.isEmpty());
    }

    @Test
    public void acknowledgesMissingCall() {
        when(calls.findByCallId("call-1")).thenReturn(Optional.empty());
        service.handle(event("room_finished").build());
        assertTrue(lastMessage().contains("reason=unknown_call"));
        verifyNoInteractions(events);
    }

    @Test
    public void unrecognizedParticipantDoesNotProduceConnectionObservation() {
        service.handle(event("participant_joined").setParticipant(
                ParticipantInfo.newBuilder().setSid("PA_2").setIdentity("999")).build());
        assertTrue(lastMessage().contains("reason=unknown_participant"));
        assertFalse(lastMessage().contains("mediaConnectedAt="));
        verifyNoInteractions(events);
    }

    @Test
    public void rejectsIncompleteRecognizedEventsBeforeQuerying() {
        for (WebhookEvent event : new WebhookEvent[]{
                event("room_finished").clearRoom().build(),
                event("room_finished").clearId().build(),
                event("room_finished").setCreatedAt(0).build(),
                event("room_finished").setCreatedAt(Long.MAX_VALUE).build(),
                event("room_started").setId("a".repeat(129)).build(),
                event("room_started").setRoom(room().setName("openchat-call-" + "a".repeat(65))).build(),
                event("room_started").setRoom(room().setSid("a".repeat(129))).build(),
                event("room_finished").setRoom(room().clearSid()).build(),
                event("participant_joined").clearParticipant().build(),
                event("participant_joined").setParticipant(ParticipantInfo.newBuilder()
                        .setSid("a".repeat(129)).setIdentity("10")).build(),
                event("participant_left").setParticipant(ParticipantInfo.newBuilder().setIdentity("10")).build()}) {
            var exception = assertThrows(ResponseStatusException.class, () -> service.handle(event));
            assertEquals(400, exception.getStatusCode().value());
        }
        verifyNoInteractions(calls, events);
    }

    @Test
    public void doesNotPlaceLineBreaksOrMetadataInLogs() {
        service.handle(event("participant_joined").setId("event\r\nforged")
                .setParticipant(ParticipantInfo.newBuilder().setSid("PA_1").setIdentity("10")
                        .setMetadata("private-metadata")).build());
        assertFalse(lastMessage().contains("\n"));
        assertFalse(lastMessage().contains("\r"));
        assertFalse(lastMessage().contains("private-metadata"));
    }

    private Room.Builder room() {
        return Room.newBuilder().setName("openchat-call-call-1").setSid("RM_1")
                .setCreationTime(EVENT_TIME - 100);
    }

    private WebhookEvent.Builder event(String type) {
        return WebhookEvent.newBuilder().setId("event-1").setEvent(type).setCreatedAt(EVENT_TIME)
                .setRoom(room()).setParticipant(ParticipantInfo.newBuilder().setSid("PA_1").setIdentity("10"));
    }

    private String lastMessage() {
        return appender.list.getLast().getFormattedMessage();
    }
}
