package com.openchat.webrtc.dao;

import cn.hutool.extra.spring.SpringUtil;
import com.openchat.common.config.JacksonConfig;
import com.openchat.rtc.domain.*;
import com.openchat.rtc.port.*;
import com.openchat.rtc.service.RtcCallService;
import com.openchat.webrtc.domain.entity.*;
import com.openchat.webrtc.mapper.RtcCallMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.StaticApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** 持久化投影与真实序列化契约，确保群被叫为空且成员版本不丢失。 */
public class RtcGroupCallMappingTest {
    private final RtcCallMapper mapper = mock(RtcCallMapper.class);
    private final RtcGroupCallDao groups = mock(RtcGroupCallDao.class);
    private final RtcCallDao dao = new RtcCallDao(mapper, mock(RtcMediaDao.class), groups);
    private StaticApplicationContext context;
    private JsonMapper json;
    private RtcCall entity;

    @Before
    public void setUp() {
        var builder = JsonMapper.builder();
        new JacksonConfig().longToStringCustomizer().customize(builder);
        json = builder.build();
        context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("jsonMapper", json);
        new SpringUtil().setApplicationContext(context);
        entity = new RtcCall();
        entity.setCallId("call");
        entity.setRoomId(9007199254740993L);
        entity.setCallerUid(10L);
        entity.setState(RtcCallState.ACCEPTED);
        entity.setMediaMode("VIDEO");
        entity.setVersion(99);
        entity.setClientRequestId("create");
        entity.setCreatedAt(Date.from(Instant.EPOCH));
        entity.setUpdatedAt(Date.from(Instant.EPOCH));
        when(mapper.selectById("call")).thenReturn(entity);
    }

    @After
    public void tearDown() { context.close(); }

    @Test
    public void groupReadsMemberVersionsAndSerializesNullCalleeWithoutDeviceSecrets() {
        var member = new RtcGroupCallMemberHistory();
        member.setUid(10L);
        member.setState(RtcParticipantState.ACCEPTED);
        member.setVersion(3);
        member.setTerminal("PC");
        member.setDeviceId("a".repeat(64));
        when(groups.list("call")).thenReturn(List.of(member));
        var calls = new RtcCallService(dao, mock(RtcPermissionPort.class),
                mock(RtcEventPublisher.class), () -> "event");
        var tree = json.readTree(json.writeValueAsString(calls.get("call", 10L)));
        assertTrue(tree.get("calleeUid").isNull());
        assertEquals("9007199254740993", tree.get("roomId").asString());
        assertEquals(99, tree.get("version").asInt());
        assertEquals(3, tree.get("participants").get(0).get("version").asInt());
        assertEquals(2, tree.get("participants").get(0).get("state").asInt());
        assertFalse(tree.has("participantDevices"));
    }

    @Test
    public void singleDerivesBothStatesWithoutReadingGroupTables() {
        entity.setCalleeUid(20L);
        var call = dao.findByCallId("call").orElseThrow();
        assertFalse(call.isGroup());
        assertEquals(Map.of(10L, RtcParticipantState.ACCEPTED, 20L, RtcParticipantState.ACCEPTED),
                call.participantStates());
        assertTrue(call.participantVersions().isEmpty());
        verifyNoInteractions(groups);
    }

    @Test
    public void groupInsertUsesMemberTablesWithoutAggregateMemberData() {
        Instant now = Instant.EPOCH;
        var call = new RtcCallRecord("call", 100L, 10L, null, RtcMediaMode.VIDEO,
                RtcCallState.RINGING, 1, "create", "room:100", now.plusSeconds(60),
                null, now, now, null, null, null, null, null, null, List.of(10L, 20L),
                Map.of(10L, RtcParticipantState.ACCEPTED, 20L, RtcParticipantState.INVITED),
                Map.of(10L, new RtcDevice("PC", "a".repeat(64))), Map.of());
        when(mapper.insert(any(RtcCall.class))).thenAnswer(invocation -> {
            RtcCall inserted = invocation.getArgument(0);
            assertNull(inserted.getCalleeUid());
            assertNull(inserted.getParticipantDevices());
            return 1;
        });
        assertTrue(dao.insert(call));
        verify(groups).insert(call);
    }
}
