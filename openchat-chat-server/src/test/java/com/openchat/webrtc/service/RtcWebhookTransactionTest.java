package com.openchat.webrtc.service;

import com.openchat.rtc.domain.*;
import com.openchat.rtc.port.*;
import com.openchat.rtc.service.RtcCallConflictException;
import com.openchat.webrtc.dao.*;
import com.openchat.webrtc.domain.entity.RtcMediaSession;
import com.openchat.webrtc.domain.entity.RtcWebhookEvent;
import com.openchat.webrtc.domain.enums.RtcMediaTransportEnum;
import com.openchat.webrtc.mapper.RtcWebhookEventMapper;
import livekit.LivekitModels;
import livekit.LivekitWebhook;
import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** 真实 Spring 事务代理和回调服务；数据库回滚、唯一约束及可靠任务存储使用替身。 */
public class RtcWebhookTransactionTest {
    private static final long NOW = 1794182400L;
    private final RtcCallDao calls = mock(RtcCallDao.class);
    private final RtcMediaDao media = mock(RtcMediaDao.class);
    private final RtcWebhookEventMapper mapper = mock(RtcWebhookEventMapper.class);
    private final RtcWebhookEventDao events = spy(new RtcWebhookEventDao(mapper));
    private final RtcMediaCleanupService liveKit = mock(RtcMediaCleanupService.class);
    private final RtcCallTerminalHandler terminal = mock(RtcCallTerminalHandler.class);
    private final RtcEventPublisher publisher = mock(RtcEventPublisher.class);
    private final RtcIdGenerator ids = mock(RtcIdGenerator.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final AtomicReference<RtcCallRecord> stored = new AtomicReference<>();
    private final Map<String, RtcWebhookEvent> rows = new LinkedHashMap<>();
    private RtcWebhookService firstNode;
    private RtcWebhookService secondNode;
    private boolean rejectClaims;

    @Before
    public void setUp() {
        stored.set(record(RtcCallState.ACCEPTED, 3, null, null));
        when(calls.findByCallId("call")).thenAnswer(_ -> Optional.of(stored.get()));
        var session = new RtcMediaSession();
        session.setTransport(RtcMediaTransportEnum.SFU);
        when(media.selectById("call")).thenReturn(session);
        when(calls.claimWebhook(any())).thenAnswer(invocation -> {
            RtcCallRecord expected = invocation.getArgument(0);
            var current = stored.get();
            if (rejectClaims) return false;
            if (!current.version().equals(expected.version()) || current.state().terminal()) return false;
            stored.set(record(current.state(), current.version() + 1, current.connectedAt(), current.endedAt()));
            return true;
        });
        when(calls.updateFromWebhook(any(), nullable(Instant.class), nullable(Instant.class))).thenAnswer(invocation -> {
            RtcCallRecord expected = invocation.getArgument(0);
            Instant connected = invocation.getArgument(1);
            Instant ended = invocation.getArgument(2);
            if (!stored.get().version().equals(expected.version())) return false;
            stored.set(record(ended == null ? RtcCallState.CONNECTED : RtcCallState.ENDED,
                    expected.version() + 1, connected, ended));
            return true;
        });
        when(mapper.insert(any(RtcWebhookEvent.class))).thenAnswer(invocation -> {
            RtcWebhookEvent row = invocation.getArgument(0);
            if (rows.putIfAbsent(row.getEventId(), row) != null) throw new DuplicateKeyException("event_id");
            return 1;
        });
        doAnswer(_ -> List.copyOf(rows.values())).when(events).listRoomEvents("call", "RM_1");
        doReturn(false).when(events).hasNewerRoom(any());
        when(liveKit.isRoomClosed("call", "RM_1")).thenReturn(true);
        when(ids.nextId()).thenReturn("notification");
        when(transactions.getTransaction(any())).thenAnswer(invocation -> {
            TransactionDefinition definition = invocation.getArgument(0);
            assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, definition.getIsolationLevel());
            return new Snapshot(stored.get(), new LinkedHashMap<>(rows));
        });
        doAnswer(invocation -> {
            Snapshot snapshot = invocation.getArgument(0);
            stored.set(snapshot.call);
            rows.clear();
            rows.putAll(snapshot.events);
            return null;
        }).when(transactions).rollback(any());
        var updates = new RtcWebhookCallService(calls, media, events, liveKit, terminal, publisher, ids);
        firstNode = proxy(new RtcWebhookService(calls, events, updates));
        secondNode = proxy(new RtcWebhookService(calls, events, updates));
    }

    @Test
    public void versionConflictRollsBackEventIdSoAnotherNodeCanRedeliverAndConnect() {
        firstNode.handle(event("join-1", "participant_joined", 10L, NOW - 30));
        RtcCallRecord before = stored.get();
        rejectClaims = true;
        var peer = event("join-2", "participant_joined", 20L, NOW - 20);
        assertThrows(RtcCallConflictException.class, () -> secondNode.handle(peer));
        assertFalse(rows.containsKey("join-2"));
        assertSame(before, stored.get());
        rejectClaims = false;
        secondNode.handle(peer);
        assertEquals(RtcCallState.CONNECTED, stored.get().state());
        assertEquals(Instant.ofEpochSecond(NOW - 20), stored.get().connectedAt());
        assertEquals(2, rows.size());
        var committed = stored.get();
        firstNode.handle(peer);
        assertSame(committed, stored.get());
        assertEquals(2, rows.size());
        verify(transactions, times(1)).rollback(any());
        verify(transactions, times(3)).commit(any());
        verifyNoInteractions(terminal, publisher);
    }

    @Test
    public void terminalTaskFailureRollsBackLogAndCallThenRetryCommitsOnce() {
        stored.set(record(RtcCallState.CONNECTED, 7, Instant.ofEpochSecond(NOW - 50), null));
        var before = stored.get();
        var finished = event("finished", "room_finished", 10L, NOW);
        doThrow(new IllegalStateException("reliable task storage failed"))
                .doNothing().when(terminal).onTerminal(any(), isNull());
        assertThrows(IllegalStateException.class, () -> firstNode.handle(finished));
        assertSame(before, stored.get());
        assertTrue(rows.isEmpty());
        verifyNoInteractions(publisher);
        secondNode.handle(finished);
        assertEquals(RtcCallState.ENDED, stored.get().state());
        assertEquals(Instant.ofEpochSecond(NOW), stored.get().endedAt());
        assertEquals(Long.valueOf(50), stored.get().durationSeconds());
        assertEquals(1, rows.size());
        var committed = stored.get();
        firstNode.handle(finished);
        assertSame(committed, stored.get());
        verify(terminal, times(2)).onTerminal(any(), isNull());
        verify(publisher, times(2)).publish(any());
        verify(transactions, times(1)).rollback(any());
        verify(transactions, times(2)).commit(any());
    }

    @Test
    public void terminalCallKeepsLateEventWithoutReopeningOrRepeatingSummary() {
        stored.set(record(RtcCallState.ENDED, 9, Instant.ofEpochSecond(NOW - 50), Instant.ofEpochSecond(NOW)));
        var ended = stored.get();
        firstNode.handle(event("late-join", "participant_joined", 10L, NOW - 40));
        assertSame(ended, stored.get());
        assertEquals(1, rows.size());
        verifyNoInteractions(media, terminal, publisher, liveKit);
    }

    private RtcWebhookService proxy(RtcWebhookService target) {
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var factory = new ProxyFactory(target);
        factory.addAdvice(interceptor);
        return (RtcWebhookService) factory.getProxy();
    }

    private RtcCallRecord record(RtcCallState state, int version, Instant connected, Instant ended) {
        Instant accepted = Instant.ofEpochSecond(NOW - 60);
        return new RtcCallRecord("call", 100L, 10L, 20L, RtcMediaMode.AUDIO, state, version,
                "request", ended == null ? "room:100" : null, null,
                ended == null ? null : RtcEndReason.RTC_CALL_ENDED.getValue(),
                accepted.minusSeconds(10), accepted, ended, accepted, connected,
                ended == null ? null : ended.getEpochSecond() - connected.getEpochSecond(), null, null);
    }

    private LivekitWebhook.WebhookEvent event(String id, String type, long uid, long at) {
        return LivekitWebhook.WebhookEvent.newBuilder().setId(id).setEvent(type).setCreatedAt(at)
                .setRoom(LivekitModels.Room.newBuilder().setSid("RM_1").setName("openchat-call-call")
                        .setCreationTime(NOW - 60))
                .setParticipant(LivekitModels.ParticipantInfo.newBuilder()
                        .setSid("PA_" + uid).setIdentity(Long.toString(uid))).build();
    }

    private static final class Snapshot extends SimpleTransactionStatus {
        private final RtcCallRecord call;
        private final Map<String, RtcWebhookEvent> events;

        private Snapshot(RtcCallRecord call, Map<String, RtcWebhookEvent> events) {
            this.call = call;
            this.events = events;
        }
    }
}
