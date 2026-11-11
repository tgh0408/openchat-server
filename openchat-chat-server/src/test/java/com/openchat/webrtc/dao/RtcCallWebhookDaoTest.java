package com.openchat.webrtc.dao;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.openchat.rtc.domain.*;
import com.openchat.webrtc.domain.entity.RtcCall;
import com.openchat.webrtc.mapper.RtcCallMapper;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** 验证回调使用真实框架版本条件、终态字段、时长及群成员收尾顺序。 */
public class RtcCallWebhookDaoTest {
    private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
    private final RtcCallMapper mapper = mock(RtcCallMapper.class);
    private final RtcMediaDao media = mock(RtcMediaDao.class);
    private final RtcGroupCallDao groups = mock(RtcGroupCallDao.class);
    private final RtcCallDao dao = new RtcCallDao(mapper, media, groups);
    private MybatisConfiguration configuration;
    private LambdaUpdateWrapper<RtcCall> written;
    private String sql;

    @Before
    public void setUp() {
        configuration = new MybatisConfiguration();
        configuration.addMapper(RtcCallMapper.class);
        when(mapper.update(any(RtcCall.class), any())).thenAnswer(invocation -> {
            RtcCall entity = invocation.getArgument(0);
            written = invocation.getArgument(1);
            var statement = configuration.getMappedStatement(RtcCallMapper.class.getName() + ".update");
            Map<String, Object> parameters = new HashMap<>();
            parameters.put(Constants.ENTITY, entity);
            parameters.put(Constants.WRAPPER, written);
            new OptimisticLockerInnerInterceptor().beforeUpdate(null, statement, parameters);
            sql = statement.getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
            String where = sql.substring(sql.indexOf("WHERE"));
            assertTrue(where, where.contains("call_id ="));
            assertTrue(where, where.contains("state ="));
            assertTrue(where, where.contains("state IN"));
            assertTrue(where, where.contains("version ="));
            assertEquals(Integer.valueOf(8), entity.getVersion());
            assertTrue(written.getParamNameValuePairs().containsValue(7));
            assertFalse(sql, sql.contains("FOR UPDATE"));
            return 1;
        });
    }

    @Test
    public void eventClaimUsesFrameworkVersionAndPreservesBusinessFields() {
        assertTrue(dao.claimWebhook(call(false)));
        assertTrue(sql, sql.contains("updated_at="));
        assertFalse(sql, sql.contains("connected_at="));
        assertFalse(sql, sql.contains("accepted_at="));
        assertFalse(sql, sql.contains("summary_message_id="));
        verifyNoInteractions(media, groups);
    }

    @Test
    public void connectionOnlyWritesMediaTimeAndConnectedState() {
        assertTrue(dao.updateFromWebhook(call(false), NOW.minusSeconds(50), null));
        assertTrue(written.getParamNameValuePairs().containsValue(RtcCallState.CONNECTED));
        assertTrue(written.getParamNameValuePairs().containsValue(Date.from(NOW.minusSeconds(50))));
        assertFalse(sql, sql.contains("ended_at="));
        assertFalse(sql, sql.contains("accepted_at="));
        assertFalse(sql, sql.contains("active_key="));
        verifyNoInteractions(media, groups);
    }

    @Test
    public void terminalWritesAllFieldsAndDurationFromConnectionTogether() {
        assertTrue(dao.updateFromWebhook(call(false), NOW.minusSeconds(50), NOW));
        for (var column : List.of("connected_at", "ended_at", "ended_by_uid", "end_reason",
                "duration_seconds", "active_key")) assertTrue(sql, sql.contains(column + "="));
        assertTrue(written.getParamNameValuePairs().containsValue(RtcCallState.ENDED));
        assertTrue(written.getParamNameValuePairs().containsValue(RtcEndReason.RTC_CALL_ENDED.getValue()));
        assertTrue(written.getParamNameValuePairs().containsValue(50L));
        assertTrue(written.getParamNameValuePairs().containsValue(Date.from(NOW)));
        assertTrue(written.getParamNameValuePairs().containsValue(null));
        verify(media).deleteByCallId("call");
        verifyNoInteractions(groups);
        verify(mapper, never()).selectById(anyString());
    }

    @Test
    public void missingMediaConnectionRetainsAcceptanceDurationFallback() {
        assertTrue(dao.updateFromWebhook(call(false), null, NOW));
        assertTrue(written.getParamNameValuePairs().containsValue(60L));
    }

    @Test
    public void groupFinishClosesMembersAfterTerminalWriteBeforeMediaDeletion() {
        var call = call(true);
        assertTrue(dao.updateFromWebhook(call, NOW.minusSeconds(50), NOW));
        var order = inOrder(mapper, groups, media);
        order.verify(mapper).update(any(RtcCall.class), any());
        order.verify(groups).closeFromWebhook(call, NOW);
        order.verify(media).deleteByCallId("call");
    }

    @Test
    public void conflictDoesNotCleanupOrRequery() {
        when(mapper.update(any(RtcCall.class), any())).thenReturn(0);
        assertFalse(dao.updateFromWebhook(call(true), NOW.minusSeconds(50), NOW));
        verify(mapper, never()).selectById(anyString());
        verifyNoInteractions(groups, media);
    }

    private RtcCallRecord call(boolean group) {
        return new RtcCallRecord("call", 100L, 10L, group ? null : 20L,
                RtcMediaMode.AUDIO, RtcCallState.ACCEPTED, 7, "request", "room:100", null,
                null, NOW.minusSeconds(100), NOW.minusSeconds(60), null,
                NOW.minusSeconds(60), null, null, null, null, List.of(10L, 20L), null);
    }
}
