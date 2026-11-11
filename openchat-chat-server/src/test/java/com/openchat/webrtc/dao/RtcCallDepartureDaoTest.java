package com.openchat.webrtc.dao;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.openchat.rtc.domain.*;
import com.openchat.webrtc.domain.entity.RtcCall;
import com.openchat.webrtc.mapper.RtcCallMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.StaticApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** 使用真实 SQL 生成器和乐观锁插件，检查成员退出与终态写入的原子边界。 */
public class RtcCallDepartureDaoTest {
    private StaticApplicationContext context;
    private MybatisConfiguration configuration;
    private RtcCallMapper mapper;
    private RtcMediaDao media;
    private RtcCallDao dao;

    @Before
    public void setUp() {
        context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("jsonMapper", JsonMapper.builder().build());
        new SpringUtil().setApplicationContext(context);
        configuration = new MybatisConfiguration();
        configuration.addMapper(RtcCallMapper.class);
        mapper = mock(RtcCallMapper.class);
        media = mock(RtcMediaDao.class);
        dao = new RtcCallDao(mapper, media, mock(RtcGroupCallDao.class));
    }

    @After
    public void closeContext() {
        context.close();
    }

    @Test
    public void lastDepartureWritesMembersAndTerminalWithFrameworkVersionGuard() {
        when(mapper.update(any(RtcCall.class), any())).thenAnswer(invocation -> {
            RtcCall entity = invocation.getArgument(0);
            LambdaUpdateWrapper<RtcCall> wrapper = invocation.getArgument(1);
            assertEquals(Integer.valueOf(7), entity.getVersion());
            var statement = configuration.getMappedStatement(RtcCallMapper.class.getName() + ".update");
            Map<String, Object> params = new HashMap<>();
            params.put(Constants.ENTITY, entity);
            params.put(Constants.WRAPPER, wrapper);
            new OptimisticLockerInnerInterceptor().beforeUpdate(null, statement, params);
            var bound = statement.getBoundSql(params);
            String sql = bound.getSql().replaceAll("\\s+", " ");
            String where = sql.substring(sql.indexOf("WHERE"));
            assertFalse(sql, sql.contains("participant_states"));
            assertFalse(sql, sql.contains("participant_uids"));
            assertTrue(sql, sql.contains("active_key="));
            assertTrue(sql, sql.contains("ended_at="));
            assertTrue(where, where.contains("version ="));
            assertTrue(where, where.contains("state ="));
            assertTrue(where, where.contains("call_id ="));
            assertEquals(Integer.valueOf(8), entity.getVersion());
            assertTrue(wrapper.getParamNameValuePairs().containsValue(7));
            var meta = configuration.newMetaObject(params);
            assertTrue(bound.getParameterMappings().stream()
                    .anyMatch(mapping -> meta.getValue(mapping.getProperty()) == null));
            return 1;
        });
        assertTrue(update(RtcCallState.ENDED));
        verify(media).deleteByCallId("call");
    }

    @Test
    public void failedLastDepartureCannotCleanMedia() {
        when(mapper.update(any(RtcCall.class), any())).thenReturn(0);
        assertFalse(update(RtcCallState.ENDED));
        verifyNoInteractions(media);
        verify(mapper, times(1)).update(any(RtcCall.class), any());
    }

    @Test
    public void nonterminalDepartureKeepsMedia() {
        when(mapper.update(any(RtcCall.class), any())).thenReturn(1);
        assertTrue(update(RtcCallState.ACCEPTED));
        verifyNoInteractions(media);
    }

    @Test
    public void expiryScanRequiresPendingInvitationAndKeepsBoundedBatch() {
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<RtcCall> wrapper = invocation.getArgument(0);
            var statement = configuration.getMappedStatement(RtcCallMapper.class.getName() + ".selectList");
            var sql = statement.getBoundSql(Map.of(Constants.WRAPPER, wrapper)).getSql();
            assertTrue(sql, sql.contains("EXISTS (SELECT 1 FROM rtc_group_call_member_history"));
            assertTrue(sql, sql.contains("callee_uid IS NOT NULL"));
            assertFalse(sql, sql.contains("participant_states"));
            assertTrue(sql, sql.contains("expires_at <="));
            assertTrue(sql, sql.contains("LIMIT 200"));
            assertTrue(wrapper.getParamNameValuePairs().containsValue(1));
            return List.of();
        });
        assertTrue(dao.findDueInvitations(1000).isEmpty());
    }

    @Test
    public void singleDeviceWriteKeepsFrameworkVersionAndDoesNotUseRemovedColumns() {
        when(mapper.update(any(RtcCall.class), any())).thenAnswer(invocation -> {
            RtcCall entity = invocation.getArgument(0);
            LambdaUpdateWrapper<RtcCall> wrapper = invocation.getArgument(1);
            var statement = configuration.getMappedStatement(RtcCallMapper.class.getName() + ".update");
            Map<String, Object> params = new HashMap<>();
            params.put(Constants.ENTITY, entity);
            params.put(Constants.WRAPPER, wrapper);
            new OptimisticLockerInnerInterceptor().beforeUpdate(null, statement, params);
            String sql = statement.getBoundSql(params).getSql().replaceAll("\\s+", " ");
            assertFalse(sql, sql.contains("participant_uids"));
            assertFalse(sql, sql.contains("participant_states"));
            assertTrue(sql, sql.contains("participant_devices="));
            assertTrue(sql, sql.contains("version ="));
            assertTrue(sql, sql.contains("state ="));
            assertFalse(sql, sql.contains("expires_at >"));
            assertTrue(wrapper.getParamNameValuePairs().values().stream()
                    .anyMatch(value -> value instanceof String json && json.contains("\"deviceId\":\"" + "a".repeat(64))));
            assertEquals(Integer.valueOf(8), entity.getVersion());
            assertTrue(wrapper.getParamNameValuePairs().containsValue(7));
            return 1;
        });
        Instant now = Instant.parse("2026-11-09T00:00:00Z");
        var previous = new RtcCallRecord("call", 100L, 10L, 20L, RtcMediaMode.AUDIO,
                RtcCallState.ACCEPTED, 7, "create", "room:100", now.minusSeconds(60), null, now, now, null)
                .withDevice(20L, new RtcDevice("PC", "a".repeat(64)));
        assertTrue(dao.updateParticipants(previous, previous.participantStates(), RtcCallState.ACCEPTED,
                null, null, now, false, false));
        verifyNoInteractions(media);
    }

    @Test
    public void singleAcceptanceStillChecksDatabaseDeadline() {
        when(mapper.update(any(RtcCall.class), any())).thenAnswer(invocation -> {
            LambdaUpdateWrapper<RtcCall> wrapper = invocation.getArgument(1);
            assertTrue(wrapper.getSqlSegment().contains("expires_at > CURRENT_TIMESTAMP(3)"));
            return 1;
        });
        Instant now = Instant.parse("2026-11-09T00:00:00Z");
        var previous = new RtcCallRecord("call", 100L, 10L, 20L, RtcMediaMode.AUDIO,
                RtcCallState.RINGING, 7, "create", "room:100", now.plusSeconds(60), null, now, now, null);
        assertTrue(dao.updateParticipants(previous, Map.of(10L, RtcParticipantState.ACCEPTED,
                20L, RtcParticipantState.ACCEPTED), RtcCallState.ACCEPTED, null, null, now, true, false));
    }

    private boolean update(RtcCallState nextState) {
        Instant now = Instant.parse("2026-11-08T00:00:00Z");
        var previous = new RtcCallRecord("call", 100L, 10L, 20L, RtcMediaMode.VIDEO,
                RtcCallState.ACCEPTED, 7, "create", "room:100", now, null, now, now, null);
        Map<Long, RtcParticipantState> states = Map.of(10L, RtcParticipantState.LEFT,
                20L, nextState.terminal() ? RtcParticipantState.LEFT : RtcParticipantState.ACCEPTED);
        return dao.updateParticipants(previous, states, nextState,
                nextState.terminal() ? RtcEndReason.RTC_CALL_ENDED.getValue() : null,
                nextState.terminal() ? 20L : null, now, false, false);
    }
}
