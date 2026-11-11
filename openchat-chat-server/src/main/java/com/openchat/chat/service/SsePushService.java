package com.openchat.chat.service;

import com.openchat.chat.dao.GroupMemberDao;
import com.openchat.chat.dao.MessageDao;
import com.openchat.chat.dao.RoomDao;
import com.openchat.chat.dao.RoomGroupDao;
import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.entity.RoomGroup;
import com.openchat.chat.domain.dto.PushMessageDTO;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.domain.vo.resp.ChatMessageResp;
import com.openchat.chat.domain.vo.resp.GroupMembersRemovedNotification;
import com.openchat.common.sse.core.SseEmitterManager;
import com.openchat.common.utils.JsonUtils;
import com.openchat.user.domain.enums.SSEPushTypeEnum;
import com.openchat.user.domain.vo.resp.FriendApplyNotification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 统一处理 SSE 推送消息，供 RocketMQ 和 LiteTopic 消费入口复用。
 */
@Service
@RequiredArgsConstructor
public class SsePushService {

    private final SseEmitterManager sseEmitterManager;
    private final RoomAccessService roomAccessService;
    private final GroupMemberDao groupMemberDao;
    private final RoomDao roomDao;
    private final RoomGroupDao roomGroupDao;
    private final MessageDao messageDao;

    /**
     * 按推送类型处理一条 SSE 消息。
     *
     * @return 消息格式和推送类型有效时返回 true，否则返回 false
     */
    public boolean push(PushMessageDTO message) {
        if (message == null) {
            return false;
        }
        String eventType = message.getEventType();
        if (eventType == null) {
            return false;
        }
        String content;
        switch (eventType) {
            case GroupMembersRemovedNotification.EVENT_TYPE:
                return pushGroupMembersRemoved(message);
            case PushMessageDTO.LOGIN_SESSION_CHANGED:
                if (message.getPushType() != SSEPushTypeEnum.USER
                        || message.getUidList() == null || message.getUidList().size() != 1
                        || message.getUidList().getFirst() == null || message.getUidList().getFirst() <= 0) {
                    return false;
                }
                sseEmitterManager.reconcileSessions(message.getUidList().getFirst());
                return true;
            case PushMessageDTO.CHAT_MESSAGE:
                if (!isValidChatMessage(message)
                        || (message.getPushType() != SSEPushTypeEnum.USER
                        && message.getPushType() != SSEPushTypeEnum.ROOM)) {
                    return false;
                }
                content = JsonUtils.toStr(message.getMsg());
                break;
            case FriendApplyNotification.EVENT_TYPE:
                FriendApplyNotification notification = message.getFriendApply();
                if (notification == null || notification.getTargetUid() == null
                        || notification.getTargetUid() <= 0
                        || !FriendApplyNotification.EVENT_TYPE.equals(notification.getEventType())
                        || message.getPushType() != SSEPushTypeEnum.USER
                        || !List.of(notification.getTargetUid()).equals(message.getUidList())) {
                    return false;
                }
                content = JsonUtils.toStr(notification);
                break;
            default:
                return false;
        }

        SSEPushTypeEnum pushType = message.getPushType();
        if (pushType == null) {
            return false;
        }
        switch (pushType) {
            case USER:
                if (message.getUidList() == null) {
                    return false;
                }
                List<Long> recipients = message.getUidList();
                if (PushMessageDTO.CHAT_MESSAGE.equals(eventType)) {
                    recipients = chatRecipients(message);
                }
                recipients.forEach(uid -> sseEmitterManager.sendMessage(uid, content));
                return true;
            case ROOM:
                return pushRoom(message, content);
            case ALL:
                sseEmitterManager.sendMessage(content);
                return true;
            default:
                return false;
        }
    }

    /**
     * 仅按热群规则推送消息，LiteTopic 消费入口使用该方法避免信任消息体中的 USER 接收人。
     *
     * @return 消息格式和推送类型有效时返回 true，否则返回 false
     */
    public boolean pushRoom(PushMessageDTO message) {
        if (!isValidChatMessage(message)
                || message.getPushType() != SSEPushTypeEnum.ROOM) {
            return false;
        }
        return pushRoom(message, JsonUtils.toStr(message.getMsg()));
    }

    private boolean pushRoom(PushMessageDTO message, String content) {
        Long roomId = message.getRoomId() == null
                ? message.getMsg().getMessage().getRoomId() : message.getRoomId();
        if (roomId == null || roomId <= 0
                || !Objects.equals(roomId, message.getMsg().getMessage().getRoomId())) {
            return false;
        }
        List<Long> recipients = hotRoomRecipients(message, roomId);
        sseEmitterManager.sendMessageToUsersAsync(recipients, content).join();
        return true;
    }

    private List<Long> chatRecipients(PushMessageDTO push) {
        var message = push.getMsg();
        Long roomId = message.getMessage().getRoomId();
        Room room = roomDao.get(roomId);
        if (room == null) return List.of();
        List<Long> recipients = push.getUidList().stream()
                .filter(uid -> uid != null && uid > 0).distinct().toList();
        return switch (room.getType()) {
            case GROUP -> {
                Set<Long> members = groupRecipients(push, roomId);
                yield recipients.stream().filter(members::contains).toList();
            }
            case FRIEND -> friendRecipients(push, recipients);
            case FILE_ASSISTANT -> recipients.stream().filter(uid -> Objects.equals(uid, room.getOwnerUid())).toList();
        };
    }

    /** 仅单聊按双方关系拉黑过滤；发送者确认、系统提示和撤回仍送达真实参与者。 */
    private List<Long> friendRecipients(PushMessageDTO push, List<Long> recipients) {
        ChatMessageResp chatMessageResp = push.getMsg();
        Long sender = chatMessageResp.getFromUser().getUid();
        Long peer = roomAccessService.requireFriendPeer(sender, chatMessageResp.getMessage().getRoomId());
        List<Long> participants = recipients.stream()
                .filter(uid -> Objects.equals(uid, sender) || Objects.equals(uid, peer)).toList();
        if (chatMessageResp.getMessage().getType() == MessageTypeEnum.SYSTEM
                || chatMessageResp.getMessage().getType() == MessageTypeEnum.RECALL)
            return participants;
        if (roomAccessService.isMyFriendBlacklisted(sender, peer)
                || roomAccessService.isMyFriendBlacklisted(peer, sender)) {
            return participants.stream().filter(uid -> Objects.equals(uid, sender)).toList();
        }
        return participants;
    }

    private List<Long> hotRoomRecipients(PushMessageDTO push, Long roomId) {
        Set<Long> members = groupRecipients(push, roomId);
        return sseEmitterManager.getConnectedUserIds().stream().filter(members::contains).toList();
    }

    /** 普通消息只发当前成员；同一条移出广播仅额外包含其被移出者，不开放历史权限。 */
    private Set<Long> groupRecipients(PushMessageDTO push, Long roomId) {
        RoomGroup group = roomGroupDao.get(roomId);
        if (group == null
                || group.getDeleteStatus() != com.openchat.common.domain.enums.YesOrNoEnum.NO) return Set.of();
        Set<Long> members = groupMemberDao.getMemberUidSet(roomId);
        if (push.getMsg().getMessage().getType() != MessageTypeEnum.SYSTEM)
            return members;
        // 不信任队列中的扩展字段来绕过成员校验，仅认可同房间已落库的移出提示。
        Message stored = messageDao.getById(RoomTypeEnum.GROUP, push.getMsg().getMessage().getId());
        if (stored != null && Objects.equals(stored.getRoomId(), roomId)
                && stored.getType() == MessageTypeEnum.SYSTEM
                && stored.getExtra() != null && stored.getExtra().getGroupRemoval() != null) {
            var removal = stored.getExtra().getGroupRemoval();
            if ("group-removal".equals(removal.getKind())
                    && removal.getMemberUid() != null && removal.getMemberUid() > 0) {
                members.add(removal.getMemberUid());
            }
        }
        return members;
    }

    private boolean isValidChatMessage(PushMessageDTO message) {
        return message != null
                && message.getMsg() != null
                && message.getMsg().getMessage() != null
                && message.getMsg().getMessage().getRoomId() != null
                && message.getMsg().getMessage().getRoomId() > 0
                && message.getMsg().getFromUser() != null
                && message.getMsg().getFromUser().getUid() != null
                && message.getMsg().getFromUser().getUid() > 0;
    }

    /** 广播不依赖热群订阅或成员缓存，迟到通知只发给本节点当前仍在群内的连接。 */
    private boolean pushGroupMembersRemoved(PushMessageDTO message) {
        GroupMembersRemovedNotification notification = message.getGroupMembersRemoved();
        if (notification == null
                || !GroupMembersRemovedNotification.EVENT_TYPE.equals(notification.getEventType())
                || message.getPushType() != SSEPushTypeEnum.ROOM
                || notification.getRoomId() == null || notification.getRoomId() <= 0
                || !Objects.equals(message.getRoomId(), notification.getRoomId())
                || notification.getRemovedUids() == null || notification.getRemovedUids().isEmpty()
                || notification.getRemovedUids().size() > 100
                || notification.getRemovedUids().stream().anyMatch(uid -> uid == null || uid <= 0)) {
            return false;
        }
        Set<Long> connected = sseEmitterManager.getConnectedUserIds();
        if (connected.isEmpty()) return true;
        Set<Long> members = groupMemberDao.getMemberUidSet(notification.getRoomId());
        List<Long> recipients = connected.stream().filter(members::contains).toList();
        sseEmitterManager.sendMessageToUsersAsync(recipients, JsonUtils.toStr(notification)).join();
        return true;
    }
}
