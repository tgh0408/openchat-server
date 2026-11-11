package com.openchat.chat.consumer;

import com.openchat.chat.dao.GroupMemberDao;
import com.openchat.chat.dao.RoomDao;
import com.openchat.chat.domain.dto.PushMessageDTO;
import com.openchat.chat.domain.entity.GroupMember;
import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.domain.event.GroupMembersChanged;
import com.openchat.chat.domain.vo.resp.RoomHistorySyncNotification;
import com.openchat.chat.service.SsePushService;
import com.openchat.common.event.SseConnectionChangedEvent;
import com.openchat.transaction.config.LiteTopicClient;
import com.openchat.common.sse.core.SseEmitterManager;
import com.openchat.common.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 热群 LiteTopic 业务适配层。
 *
 * <p>每个节点使用独立消费组，只订阅本节点当前承载用户所在的热群，避免多个节点之间发生消费组负载均衡导致漏投递。</p>
 * SDK 配置、重试调度和资源生命周期由框架管理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiteTopicPush {

    private static final String ROOM_PREFIX = "room-";

    private final GroupMemberDao groupMemberDao;
    private final RoomDao roomDao;
    private final SseEmitterManager sseEmitterManager;
    private final SsePushService ssePushService;
    private final LiteTopicClient client;
    private final Set<Long> subscribedRooms = ConcurrentHashMap.newKeySet();

    @EventListener(ContextRefreshedEvent.class)
    public void start() {
        client.start(this::consume, this::restoreSubscriptions);
    }

    private void restoreSubscriptions() {
        subscribedRooms.clear();
        sseEmitterManager.getConnectedUserIds().forEach(this::scheduleUserReconcile);
    }

    public boolean isReady() {
        return client.isReady();
    }

    /**
     * 判断本节点是否已经建立指定热群的 LiteTopic 订阅。
     *
     * @param roomId 房间 ID
     * @return 已订阅返回 true，否则返回 false
     */
    public boolean isRoomSubscribed(Long roomId) {
        return roomId != null && client.isSubscribed(roomTopic(roomId));
    }

    public boolean send(PushMessageDTO pushMessage) {
        if (!isReady() || pushMessage == null
                || pushMessage.getRoomId() == null || pushMessage.getMsg() == null) {
            return false;
        }
        if (!isRoomSubscribed(pushMessage.getRoomId())) {
            scheduleRoomReconcile(pushMessage.getRoomId());
            log.debug("LiteTopic room is not subscribed yet, use legacy SSE fallback, roomId={}",
                    pushMessage.getRoomId());
            return false;
        }
        byte[] body = JsonUtils.toStr(pushMessage).getBytes(StandardCharsets.UTF_8);
        return client.send(roomTopic(pushMessage.getRoomId()), messageKey(pushMessage), body);
    }

    @EventListener
    public void onSseConnectionChanged(SseConnectionChangedEvent event) {
        scheduleUserReconcile(event.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onGroupMembersChanged(GroupMembersChanged event) {
        scheduleRoomReconcile(event.roomId());
    }

    private ConsumeResult consume(MessageView messageView) {
        try {
            String liteTopic = messageView.getLiteTopic().orElse(null);
            Long roomId = parseRoomId(liteTopic);
            if (roomId == null) {
                log.error("LiteTopic message has no valid lite topic, topic={}, liteTopic={}, messageId={}, "
                                + "properties={}",
                        messageView.getTopic(), liteTopic, messageView.getMessageId(), messageView.getProperties());
                return ConsumeResult.FAILURE;
            }
            PushMessageDTO pushMessage = JsonUtils.toObj(
                    StandardCharsets.UTF_8.decode(messageView.getBody()).toString(), PushMessageDTO.class);
            if (pushMessage == null || pushMessage.getMsg() == null) {
                log.error("LiteTopic message body is invalid, roomId={}, liteTopic={}, messageId={}",
                        roomId, liteTopic, messageView.getMessageId());
                return ConsumeResult.FAILURE;
            }
            pushMessage.setRoomId(roomId);
            if (!ssePushService.pushRoom(pushMessage)) {
                log.error("LiteTopic message body is not a valid room push, roomId={}, liteTopic={}, messageId={}",
                        roomId, liteTopic, messageView.getMessageId());
                return ConsumeResult.FAILURE;
            }
            log.debug("LiteTopic message consumed, roomId={}, liteTopic={}, messageId={}",
                    roomId, liteTopic, messageView.getMessageId());
            return ConsumeResult.SUCCESS;
        } catch (RuntimeException exception) {
            log.error("Failed to consume LiteTopic message", exception);
            return ConsumeResult.FAILURE;
        }
    }

    private void scheduleUserReconcile(Long uid) {
        if (uid == null || !isReady()) {
            return;
        }
        client.executeSubscription(() -> reconcileUser(uid));
    }

    private void scheduleRoomReconcile(Long roomId) {
        if (roomId == null || !isReady()) {
            return;
        }
        client.executeSubscription(() -> reconcileRoom(roomId));
    }

    private void reconcileUser(Long uid) {
        Set<Long> roomIds = new HashSet<>(subscribedRooms);
        List<GroupMember> memberships = groupMemberDao.getUserRoom(uid);
        memberships.stream()
                .map(GroupMember::getRoomId)
                .filter(Objects::nonNull)
                .forEach(roomIds::add);
        roomIds.forEach(this::reconcileRoom);
    }

    private void reconcileRoom(Long roomId) {
        Room room = roomDao.get(roomId);
        if (room == null || room.getType() != RoomTypeEnum.GROUP || !room.isHotRoom()) {
            unsubscribe(roomId);
            return;
        }
        Set<Long> members = groupMemberDao.getMemberUidSet(roomId);
        boolean shouldSubscribe = sseEmitterManager.getConnectedUserIds().stream().anyMatch(members::contains);
        if (shouldSubscribe) {
            subscribe(roomId, members);
        } else {
            unsubscribe(roomId);
        }
    }

    private void subscribe(Long roomId, Set<Long> members) {
        if (isRoomSubscribed(roomId) || subscribedRooms.contains(roomId)) {
            return;
        }
        try {
            if (!client.subscribe(roomTopic(roomId))) {
                return;
            }
            subscribedRooms.add(roomId);
            log.info("LiteTopic room subscribed, roomId={}, liteTopic={}", roomId, roomTopic(roomId));
        } catch (ClientException | RuntimeException exception) {
            subscribedRooms.remove(roomId);
            log.warn("Failed to subscribe LiteTopic, roomId={}", roomId, exception);
            client.scheduleSubscription(() -> reconcileRoom(roomId), 2, TimeUnit.SECONDS);
            return;
        }
        notifyHistorySync(roomId, members);
    }

    /** 只通知本机在线成员，补偿其首次历史查询与订阅建立之间的空窗。 */
    private void notifyHistorySync(Long roomId, Set<Long> members) {
        try {
            List<Long> recipients = sseEmitterManager.getConnectedUserIds().stream()
                    .filter(members::contains).toList();
            String content = JsonUtils.toStr(new RoomHistorySyncNotification(roomId));
            sseEmitterManager.sendMessageToUsersAsync(recipients, content)
                    .whenComplete((ignored, exception) -> {
                        if (exception != null) {
                            log.warn("Failed to notify LiteTopic history sync, roomId={}", roomId, exception);
                        }
                    });
        } catch (RuntimeException exception) {
            // 通知失败不撤销已建立的订阅，也不伪装成订阅失败。
            log.warn("Failed to notify LiteTopic history sync, roomId={}", roomId, exception);
        }
    }

    private void unsubscribe(Long roomId) {
        try {
            client.unsubscribe(roomTopic(roomId));
            subscribedRooms.remove(roomId);
        } catch (ClientException | RuntimeException exception) {
            log.warn("Failed to unsubscribe LiteTopic, roomId={}", roomId, exception);
        }
    }

    private String roomTopic(Long roomId) {
        return ROOM_PREFIX + roomId;
    }

    private String messageKey(PushMessageDTO pushMessage) {
        if (pushMessage.getMsg().getMessage() != null && pushMessage.getMsg().getMessage().getId() != null) {
            return String.valueOf(pushMessage.getMsg().getMessage().getId());
        }
        return roomTopic(pushMessage.getRoomId());
    }

    private Long parseRoomId(String liteTopic) {
        if (liteTopic == null || !liteTopic.startsWith(ROOM_PREFIX)) {
            return null;
        }
        try {
            return Long.parseLong(liteTopic.substring(ROOM_PREFIX.length()));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

}
