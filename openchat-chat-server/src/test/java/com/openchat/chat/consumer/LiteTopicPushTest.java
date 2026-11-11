package com.openchat.chat.consumer;

import com.openchat.chat.dao.GroupMemberDao;
import com.openchat.chat.dao.RoomDao;
import com.openchat.chat.domain.entity.GroupMember;
import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.enums.HotFlagEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.service.SsePushService;
import com.openchat.common.event.SseConnectionChangedEvent;
import com.openchat.common.sse.core.SseEmitterManager;
import com.openchat.common.utils.SpringUtils;
import com.openchat.transaction.config.LiteTopicClient;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用本机连接与 SDK 替身核对订阅通知，不连接 Redis、数据库或 MQ。 */
public class LiteTopicPushTest {
    private static final long ROOM_ID = 9007199254740993L;
    private static final String TOPIC = "room-" + ROOM_ID;
    private GroupMemberDao members;
    private RoomDao rooms;
    private SseEmitterManager emitters;
    private LiteTopicClient client;
    private LiteTopicPush push;

    @BeforeClass
    public static void initializeJson() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("jsonMapper", JsonMapper.builder().build());
        new SpringUtils().postProcessBeanFactory(factory);
    }

    @Before
    public void setUp() {
        members = mock(GroupMemberDao.class);
        rooms = mock(RoomDao.class);
        emitters = mock(SseEmitterManager.class);
        client = mock(LiteTopicClient.class);
        push = new LiteTopicPush(members, rooms, emitters, mock(SsePushService.class), client);
        when(client.isReady()).thenReturn(true);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(client).executeSubscription(any());
        Room room = new Room();
        room.setId(ROOM_ID);
        room.setType(RoomTypeEnum.GROUP);
        room.setHotFlag(HotFlagEnum.YES);
        when(rooms.get(ROOM_ID)).thenReturn(room);
        GroupMember member = new GroupMember();
        member.setRoomId(ROOM_ID);
        when(members.getUserRoom(1L)).thenReturn(List.of(member));
        when(members.getMemberUidSet(ROOM_ID)).thenReturn(Set.of(1L, 2L));
        when(emitters.getConnectedUserIds()).thenReturn(Set.of(1L, 3L));
        when(emitters.sendMessageToUsersAsync(anyCollection(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    public void successfulSubscriptionNotifiesOnlyLocalOnlineMembersOnce() throws Exception {
        when(client.subscribe(TOPIC)).thenReturn(true);

        push.onSseConnectionChanged(new SseConnectionChangedEvent(1L, true));
        push.onSseConnectionChanged(new SseConnectionChangedEvent(1L, true));

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(emitters).sendMessageToUsersAsync(eq(List.of(1L)), content.capture());
        assertTrue(content.getValue().contains("\"eventType\":\"ROOM_HISTORY_SYNC_REQUIRED\""));
        assertTrue(content.getValue().contains("\"roomId\":\"" + ROOM_ID + "\""));
        verify(client).subscribe(TOPIC);
    }

    @Test
    public void retrySuccessNotifiesAfterSubscriptionIsEstablished() throws Exception {
        when(client.subscribe(TOPIC)).thenThrow(new IllegalStateException("unavailable")).thenReturn(true);

        push.onSseConnectionChanged(new SseConnectionChangedEvent(1L, true));
        verify(emitters, never()).sendMessageToUsersAsync(anyCollection(), anyString());
        ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
        verify(client).scheduleSubscription(retry.capture(), eq(2L), eq(TimeUnit.SECONDS));

        retry.getValue().run();

        verify(emitters).sendMessageToUsersAsync(eq(List.of(1L)), anyString());
        verify(client, times(2)).subscribe(TOPIC);
    }

    @Test
    public void notificationFailureDoesNotInvalidateOrRepeatSubscription() throws Exception {
        when(client.subscribe(TOPIC)).thenReturn(true);
        when(emitters.sendMessageToUsersAsync(anyCollection(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("queue full")));

        push.onSseConnectionChanged(new SseConnectionChangedEvent(1L, true));
        push.onSseConnectionChanged(new SseConnectionChangedEvent(1L, true));

        verify(client).subscribe(TOPIC);
        verify(client, never()).scheduleSubscription(any(), anyLong(), any());
        verify(emitters).sendMessageToUsersAsync(anyCollection(), anyString());
    }

    @Test
    public void unavailableSubscriptionDoesNotNotify() throws Exception {
        when(client.subscribe(TOPIC)).thenReturn(false);

        push.onSseConnectionChanged(new SseConnectionChangedEvent(1L, true));

        verify(emitters, never()).sendMessageToUsersAsync(anyCollection(), anyString());
    }
}
