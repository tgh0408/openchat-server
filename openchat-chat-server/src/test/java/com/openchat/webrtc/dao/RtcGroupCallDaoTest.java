package com.openchat.webrtc.dao;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.openchat.rtc.domain.*;
import com.openchat.rtc.service.RtcCallConflictException;
import com.openchat.webrtc.domain.entity.*;
import com.openchat.webrtc.mapper.*;
import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** 验证真实 SQL、框架成员版本和失败事务边界，不连接运行中的数据库。 */
public class RtcGroupCallDaoTest {
    private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
    private final RtcDevice device = new RtcDevice("PC", "a".repeat(64));
    private final RtcCallMapper calls = mock(RtcCallMapper.class);
    private final RtcGroupCallMemberHistoryMapper history = mock(RtcGroupCallMemberHistoryMapper.class);
    private final RtcGroupCallMemberMapper members = mock(RtcGroupCallMemberMapper.class);
    private final RtcMediaDao media = mock(RtcMediaDao.class);
    private final RtcGroupCallDao dao = new RtcGroupCallDao(history, members, calls, media);
    private MybatisConfiguration configuration;
    private RtcGroupCallMemberHistory row;
    private RtcCall aggregate;
    private RtcCallRecord snapshot;

    @Before
    public void setUp() {
        configuration = new MybatisConfiguration();
        configuration.addMapper(RtcCallMapper.class);
        configuration.addMapper(RtcGroupCallMemberHistoryMapper.class);
        configuration.addMapper(RtcGroupCallMemberMapper.class);
        row = new RtcGroupCallMemberHistory();
        row.setCallId("call");
        row.setRoomId(100L);
        row.setUid(20L);
        row.setVersion(3);
        row.setState(RtcParticipantState.INVITED);
        aggregate = new RtcCall();
        aggregate.setCallId("call");
        aggregate.setState(RtcCallState.ACCEPTED);
        aggregate.setVersion(99);
        aggregate.setAcceptedAt(Date.from(NOW));
        snapshot = new RtcCallRecord("call", 100L, 10L, null, RtcMediaMode.VIDEO,
                RtcCallState.ACCEPTED, 7, "create", "room:100", NOW.plusSeconds(60),
                null, NOW, NOW, null, NOW, null, null, null, null, List.of(10L, 20L),
                Map.of(10L, RtcParticipantState.ACCEPTED, 20L, RtcParticipantState.INVITED),
                Map.of(10L, device), Map.of(10L, 1, 20L, 3));
        when(history.selectOne(any())).thenReturn(row);
        when(history.update(any(RtcGroupCallMemberHistory.class), any())).thenReturn(1);
        when(history.insert(any(RtcGroupCallMemberHistory.class))).thenReturn(1);
        when(members.insert(any(RtcGroupCallMember.class))).thenReturn(1);
        when(members.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
        when(members.selectCount(any())).thenReturn(0L);
        when(calls.update(nullable(RtcCall.class), any())).thenReturn(1);
        when(calls.selectById("call")).thenReturn(aggregate);
    }

    @Test
    public void joinUsesMemberPluginVersionAndNotificationSequenceWithoutOldAggregateVersion() {
        when(calls.update(isNull(), any())).thenAnswer(invocation -> {
            LambdaUpdateWrapper<RtcCall> wrapper = invocation.getArgument(1);
            String sql = sql(RtcCallMapper.class, "update", null, wrapper, false);
            String where = sql.substring(sql.indexOf("WHERE"));
            assertFalse(sql, sql.contains("accepted_count"));
            assertTrue(sql, sql.contains("version = version + 1"));
            assertTrue(where, where.contains("callee_uid IS NULL"));
            assertTrue(where, where.contains("state IN"));
            assertFalse(where, where.contains("version"));
            assertFalse(sql, sql.contains("participant_states"));
            return 1;
        });
        when(history.update(any(RtcGroupCallMemberHistory.class), any())).thenAnswer(invocation -> {
            RtcGroupCallMemberHistory entity = invocation.getArgument(0);
            LambdaUpdateWrapper<RtcGroupCallMemberHistory> wrapper = invocation.getArgument(1);
            String sql = sql(RtcGroupCallMemberHistoryMapper.class, "update", entity, wrapper, true);
            String where = sql.substring(sql.indexOf("WHERE"));
            assertTrue(where, where.contains("call_id ="));
            assertTrue(where, where.contains("uid ="));
            assertTrue(where, where.contains("state ="));
            assertTrue(where, where.contains("version ="));
            assertEquals(Integer.valueOf(4), entity.getVersion());
            assertTrue(wrapper.getParamNameValuePairs().containsValue(3));
            assertTrue(sql, sql.contains("left_at="));
            return 1;
        });
        assertTrue(dao.update(snapshot, 20L, RtcParticipantState.ACCEPTED, device, 3, NOW));
        verify(members).insert(argThat((RtcGroupCallMember member) ->
                member.getCallId().equals("call") && member.getDeviceId().equals(device.deviceId())));
        verifyNoInteractions(media);
        verify(members, never()).selectCount(any());
    }

    @Test
    public void staleMemberVersionCannotTouchAggregate() {
        assertFalse(dao.update(snapshot, 20L, RtcParticipantState.ACCEPTED, device, 2, NOW));
        verifyNoInteractions(calls, members, media);
    }

    @Test
    public void uninvitedJoinInsertsKnownFieldsOnce() {
        when(history.selectOne(any())).thenReturn(null);
        assertTrue(dao.update(snapshot, 30L, RtcParticipantState.ACCEPTED, device, 1, NOW));
        verify(history).insert(argThat((RtcGroupCallMemberHistory member) ->
                member.getUid().equals(30L) && member.getVersion() == 1
                        && member.getState() == RtcParticipantState.ACCEPTED
                        && member.getTerminal().equals("PC") && member.getAcceptedAt().equals(Date.from(NOW))));
        verify(history, never()).update(any(RtcGroupCallMemberHistory.class), any());
    }

    @Test
    public void lastLeaveCountsCurrentCallAfterDeletionAndGuardsTerminalWithPlugin() {
        row.setState(RtcParticipantState.ACCEPTED);
        row.setTerminal(device.terminal());
        row.setDeviceId(device.deviceId());
        when(members.delete(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<RtcGroupCallMember> wrapper = invocation.getArgument(0);
            String sql = wrapper.getSqlSegment();
            for (String column : List.of("room_id", "uid", "call_id", "terminal", "device_id"))
                assertTrue(sql, sql.contains(column + " ="));
            assertTrue(wrapper.getParamNameValuePairs().containsValue("call"));
            assertTrue(wrapper.getParamNameValuePairs().containsValue(device.deviceId()));
            return 1;
        });
        when(calls.update(any(RtcCall.class), any())).thenAnswer(invocation -> {
            RtcCall entity = invocation.getArgument(0);
            LambdaUpdateWrapper<RtcCall> wrapper = invocation.getArgument(1);
            String sql = sql(RtcCallMapper.class, "update", entity, wrapper, true);
            String where = sql.substring(sql.indexOf("WHERE"));
            assertTrue(where, where.contains("state ="));
            assertFalse(sql, sql.contains("accepted_count"));
            assertTrue(where, where.contains("version ="));
            assertTrue(sql, sql.contains("active_key="));
            assertEquals(RtcCallState.ENDED, entity.getState());
            assertEquals(Integer.valueOf(100), entity.getVersion());
            return 1;
        });
        when(members.selectCount(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<RtcGroupCallMember> wrapper = invocation.getArgument(0);
            var statement = configuration.getMappedStatement(RtcGroupCallMemberMapper.class.getName() + ".selectCount");
            String sql = statement.getBoundSql(Map.of(Constants.WRAPPER, wrapper))
                    .getSql().replaceAll("\\s+", " ");
            assertTrue(sql, sql.contains("COUNT"));
            assertTrue(sql, sql.contains("rtc_group_call_member"));
            assertTrue(sql, sql.contains("room_id ="));
            assertTrue(sql, sql.contains("call_id ="));
            assertFalse(sql, sql.contains(" uid ="));
            assertTrue(wrapper.getParamNameValuePairs().containsValue(100L));
            assertTrue(wrapper.getParamNameValuePairs().containsValue("call"));
            return 0L;
        });
        assertTrue(dao.update(snapshot, 20L, RtcParticipantState.LEFT, device, 3, NOW));
        var order = inOrder(calls, history, members, media);
        order.verify(calls).update(isNull(), any());
        order.verify(history).update(any(RtcGroupCallMemberHistory.class), any());
        order.verify(members).delete(any(LambdaQueryWrapper.class));
        order.verify(members).selectCount(any());
        order.verify(calls).selectById("call");
        order.verify(calls).update(any(RtcCall.class), any());
        order.verify(media).deleteByCallId("call");
    }

    @Test
    public void remainingCurrentMemberKeepsCallAndMediaWithoutReadingTerminalMetadata() {
        row.setState(RtcParticipantState.ACCEPTED);
        row.setTerminal(device.terminal());
        row.setDeviceId(device.deviceId());
        when(members.selectCount(any())).thenReturn(1L);
        assertTrue(dao.update(snapshot, 20L, RtcParticipantState.LEFT, device, 3, NOW));
        verify(calls, never()).selectById(anyString());
        verify(calls, never()).update(any(RtcCall.class), any());
        verifyNoInteractions(media);
    }

    @Test
    public void endedCallRejectsStaleJoinBeforeTouchingCurrentMembers() {
        when(calls.update(isNull(), any())).thenReturn(0);
        assertFalse(dao.update(snapshot, 20L, RtcParticipantState.ACCEPTED, device, 3, NOW));
        verify(history, never()).update(any(RtcGroupCallMemberHistory.class), any());
        verifyNoInteractions(members, media);
    }

    @Test
    public void historyConflictRollsBackAlreadyChangedAggregateWithoutRetry() {
        when(history.update(any(RtcGroupCallMemberHistory.class), any())).thenReturn(0);
        var transactions = mock(PlatformTransactionManager.class);
        var status = new SimpleTransactionStatus();
        when(transactions.getTransaction(any())).thenAnswer(invocation -> {
            TransactionDefinition definition = invocation.getArgument(0);
            assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, definition.getIsolationLevel());
            return status;
        });
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var factory = new ProxyFactory(dao);
        factory.addAdvice(interceptor);
        var proxy = (RtcGroupCallDao) factory.getProxy();
        assertThrows(RtcCallConflictException.class, () ->
                proxy.update(snapshot, 20L, RtcParticipantState.ACCEPTED, device, 3, NOW));
        verify(transactions).rollback(status);
        verify(transactions, never()).commit(any());
        verify(calls, times(1)).update(isNull(), any());
        verifyNoInteractions(members, media);
    }

    @Test
    public void webhookClosesAcceptedAndInvitedMembersWithTheirOwnVersions() {
        var accepted = new RtcGroupCallMemberHistory();
        accepted.setCallId("call");
        accepted.setUid(10L);
        accepted.setRoomId(100L);
        accepted.setVersion(4);
        accepted.setState(RtcParticipantState.ACCEPTED);
        var rejected = new RtcGroupCallMemberHistory();
        rejected.setUid(30L);
        rejected.setState(RtcParticipantState.REJECTED);
        when(history.selectList(any())).thenReturn(List.of(accepted, row, rejected));
        when(history.update(any(RtcGroupCallMemberHistory.class), any())).thenAnswer(invocation -> {
            RtcGroupCallMemberHistory member = invocation.getArgument(0);
            LambdaUpdateWrapper<RtcGroupCallMemberHistory> condition = invocation.getArgument(1);
            int version = member.getVersion();
            String sql = sql(RtcGroupCallMemberHistoryMapper.class, "update", member, condition, true);
            assertTrue(sql, sql.contains("version ="));
            assertTrue(sql, sql.contains("call_id ="));
            assertTrue(sql, sql.contains("uid ="));
            assertTrue(sql, sql.contains("state ="));
            assertEquals(Integer.valueOf(version + 1), member.getVersion());
            return 1;
        });
        when(members.delete(any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<RtcGroupCallMember> condition = invocation.getArgument(0);
            assertTrue(condition.getSqlSegment().contains("room_id ="));
            assertTrue(condition.getSqlSegment().contains("call_id ="));
            assertTrue(condition.getParamNameValuePairs().containsValue("call"));
            assertTrue(condition.getParamNameValuePairs().containsValue(100L));
            return 1;
        });
        dao.closeFromWebhook(snapshot, NOW);
        assertEquals(RtcParticipantState.LEFT, accepted.getState());
        assertEquals(Date.from(NOW), accepted.getLeftAt());
        assertEquals(RtcParticipantState.EXPIRED, row.getState());
        assertNull(row.getLeftAt());
        assertEquals(RtcParticipantState.REJECTED, rejected.getState());
        verify(history, times(2)).update(any(RtcGroupCallMemberHistory.class), any());
        verifyNoInteractions(calls, media);
    }

    @Test
    public void webhookMemberConflictStopsBeforeDeletingCurrentMembership() {
        when(history.selectList(any())).thenReturn(List.of(row));
        when(history.update(any(RtcGroupCallMemberHistory.class), any())).thenReturn(0);
        assertThrows(RtcCallConflictException.class, () -> dao.closeFromWebhook(snapshot, NOW));
        verifyNoInteractions(members, calls, media);
    }

    @Test
    public void webhookRequiresCurrentMembershipDeleteCountToMatchAcceptedMembers() {
        row.setState(RtcParticipantState.ACCEPTED);
        when(history.selectList(any())).thenReturn(List.of(row));
        when(members.delete(any(LambdaQueryWrapper.class))).thenReturn(0);
        assertThrows(RtcCallConflictException.class, () -> dao.closeFromWebhook(snapshot, NOW));
    }

    private String sql(Class<?> mapper, String operation, Object entity, Object wrapper, boolean versioned) {
        var statement = configuration.getMappedStatement(mapper.getName() + "." + operation);
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.ENTITY, entity);
        params.put(Constants.WRAPPER, wrapper);
        if (versioned) new OptimisticLockerInnerInterceptor().beforeUpdate(null, statement, params);
        return statement.getBoundSql(params).getSql().replaceAll("\\s+", " ");
    }
}
