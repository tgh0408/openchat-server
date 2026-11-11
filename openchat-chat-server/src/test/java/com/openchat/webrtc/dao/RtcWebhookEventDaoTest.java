package com.openchat.webrtc.dao;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.openchat.common.exception.BusinessException;
import com.openchat.webrtc.domain.entity.RtcWebhookEvent;
import com.openchat.webrtc.mapper.RtcWebhookEventMapper;
import org.junit.Before;
import org.junit.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** 真实 MyBatis-Plus SQL 与模拟唯一约束回归，不连接运行中的数据库。 */
public class RtcWebhookEventDaoTest {
    private RtcWebhookEventMapper mapper;
    private RtcWebhookEventDao dao;

    @Before
    public void setUp() {
        mapper = mock(RtcWebhookEventMapper.class);
        dao = new RtcWebhookEventDao(mapper);
    }

    @Test
    public void standardInsertIncludesExternalIdAndObservationFields() {
        var configuration = new MybatisConfiguration();
        configuration.addMapper(RtcWebhookEventMapper.class);
        var row = event();
        row.setParticipantSid("PA_1");
        row.setUid(10L);
        row.setRoomCreatedAtSeconds(1794182300L);
        when(mapper.insert(row)).thenAnswer(_ -> {
            var statement = configuration.getMappedStatement(RtcWebhookEventMapper.class.getName() + ".insert");
            String sql = statement.getBoundSql(row)
                    .getSql().replaceAll("\\s+", " ");
            assertTrue(sql, sql.contains("INSERT INTO rtc_webhook_event"));
            for (String column : new String[]{"event_id", "call_id", "event_type", "room_sid",
                    "participant_sid", "uid", "occurred_at_seconds", "room_created_at_seconds", "received_at"}) {
                assertTrue(sql, sql.contains(column));
            }
            return 1;
        });
        assertTrue(dao.insertIfAbsent(row));
        verify(mapper).insert(row);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    public void duplicateAcknowledgesOriginalWithoutQueryOrUpdate() {
        var row = event();
        when(mapper.insert(row)).thenThrow(new DuplicateKeyException("event_id"));
        assertFalse(dao.insertIfAbsent(row));
        verify(mapper).insert(row);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    public void unsuccessfulWriteAndNonDuplicateConstraintFailureAreNotAcknowledged() {
        var row = event();
        when(mapper.insert(row)).thenReturn(0);
        assertThrows(BusinessException.class, () -> dao.insertIfAbsent(row));
        var failure = new DataIntegrityViolationException("invalid column");
        when(mapper.insert(row)).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class, () -> dao.insertIfAbsent(row)));
        verify(mapper, times(2)).insert(row);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    public void twoHandlersAndLostAcknowledgmentKeepFirstPersistedEvent() throws Exception {
        Map<String, RtcWebhookEvent> committed = new ConcurrentHashMap<>();
        when(mapper.insert(any(RtcWebhookEvent.class))).thenAnswer(invocation -> {
            RtcWebhookEvent row = invocation.getArgument(0);
            if (committed.putIfAbsent(row.getEventId(), row) != null) {
                throw new DuplicateKeyException("event_id");
            }
            return 1;
        });
        var secondNode = new RtcWebhookEventDao(mapper);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return dao.insertIfAbsent(event());
            });
            var second = executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return secondNode.insertIfAbsent(event());
            });
            start.countDown();
            assertNotEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        }
        var original = committed.get("event-1");
        var redelivery = event();
        redelivery.setReceivedAt(new Date(original.getReceivedAt().getTime() + 1000));
        assertFalse(secondNode.insertIfAbsent(redelivery));
        assertEquals(1, committed.size());
        assertSame(original, committed.get("event-1"));
    }

    private RtcWebhookEvent event() {
        var row = new RtcWebhookEvent();
        row.setEventId("event-1");
        row.setCallId("call-1");
        row.setEventType("participant_joined");
        row.setRoomSid("RM_1");
        row.setOccurredAtSeconds(1794182400L);
        row.setReceivedAt(new Date());
        return row;
    }
}
