package com.openchat.webrtc.dao;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.openchat.rtc.domain.RtcCallState;
import com.openchat.webrtc.domain.entity.RtcCall;
import com.openchat.webrtc.mapper.RtcCallMapper;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** 使用实际 MyBatis-Plus SQL 及乐观锁插件检查摘要认领条件，不连接数据库。 */
public class RtcCallSummaryDaoTest {
    private RtcCallMapper mapper;
    private RtcCallDao dao;
    private RtcCall call;
    private MybatisConfiguration configuration;

    @Before
    public void setUp() {
        configuration = new MybatisConfiguration();
        configuration.addMapper(RtcCallMapper.class);
        mapper = mock(RtcCallMapper.class);
        dao = new RtcCallDao(mapper, mock(RtcMediaDao.class), mock(RtcGroupCallDao.class));
        call = new RtcCall();
        call.setCallId("call");
        call.setVersion(7);
        call.setState(RtcCallState.ENDED);
        when(mapper.selectById("call")).thenReturn(call);
    }

    @Test
    public void claimRequiresTerminalStateEmptySummaryAndFrameworkVersion() {
        when(mapper.update(any(RtcCall.class), any())).thenAnswer(invocation -> {
            RtcCall entity = invocation.getArgument(0);
            LambdaUpdateWrapper<RtcCall> wrapper = invocation.getArgument(1);
            var statement = configuration.getMappedStatement(RtcCallMapper.class.getName() + ".update");
            Map<String, Object> parameters = new HashMap<>();
            parameters.put(Constants.ENTITY, entity);
            parameters.put(Constants.WRAPPER, wrapper);
            new OptimisticLockerInnerInterceptor().beforeUpdate(null, statement, parameters);
            String sql = statement.getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
            String where = sql.substring(sql.indexOf("WHERE"));
            assertTrue(sql, sql.contains("summary_message_id="));
            assertTrue(where, where.contains("summary_message_id IS NULL"));
            assertTrue(where, where.contains("call_id ="));
            assertTrue(where, where.contains("state ="));
            assertTrue(where, where.contains("version ="));
            assertTrue(wrapper.getParamNameValuePairs().containsValue(RtcCallState.ENDED));
            assertTrue(wrapper.getParamNameValuePairs().containsValue(99L));
            assertTrue(wrapper.getParamNameValuePairs().containsValue(7));
            assertEquals(Integer.valueOf(8), entity.getVersion());
            return 1;
        });
        assertTrue(dao.claimSummaryMessage("call", 99L));
    }

    @Test
    public void missingActiveOrAlreadyClaimedCallIsNotUpdated() {
        when(mapper.selectById("call")).thenReturn(null);
        assertFalse(dao.claimSummaryMessage("call", 99L));
        when(mapper.selectById("call")).thenReturn(call);
        for (var state : new RtcCallState[]{RtcCallState.RINGING, RtcCallState.ACCEPTED, RtcCallState.CONNECTED}) {
            call.setState(state);
            assertFalse(dao.claimSummaryMessage("call", 99L));
        }
        call.setState(RtcCallState.ENDED);
        call.setSummaryMessageId(100L);
        assertFalse(dao.claimSummaryMessage("call", 99L));
        verify(mapper, never()).update(any(RtcCall.class), any());
    }

    @Test
    public void failedClaimDoesNotRequeryOrOverwriteWinner() {
        when(mapper.update(any(RtcCall.class), any())).thenReturn(0);
        assertFalse(dao.claimSummaryMessage("call", 99L));
        verify(mapper, times(1)).selectById("call");
        verify(mapper, times(1)).update(any(RtcCall.class), any());
    }
}
