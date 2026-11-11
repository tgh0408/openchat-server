package com.openchat.chat.domain.dto;

import com.openchat.chat.domain.vo.resp.ChatMessageResp;
import com.openchat.chat.domain.vo.resp.GroupMembersRemovedNotification;

import com.openchat.user.domain.enums.SSEPushTypeEnum;
import com.openchat.user.domain.vo.resp.FriendApplyNotification;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Description: 推送给用户的消息对象
 * Author: 
 * Date: 2023-08-12
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PushMessageDTO implements Serializable {
    public static final String CHAT_MESSAGE = "CHAT_MESSAGE";
    public static final String LOGIN_SESSION_CHANGED = "LOGIN_SESSION_CHANGED";

    /** 广播只触发权威会话复核，不传 JWT，重复或乱序消息不能误踢当前会话。 */
    public static PushMessageDTO sessionChanged(Long uid) {
        PushMessageDTO push = new PushMessageDTO();
        push.eventType = LOGIN_SESSION_CHANGED;
        push.uidList = List.of(uid);
        push.pushType = SSEPushTypeEnum.USER;
        return push;
    }

    /** 业务事件类型，与收件人范围 pushType、聊天正文 type 分开。 */
    private String eventType = CHAT_MESSAGE;

    private FriendApplyNotification friendApply;

    private GroupMembersRemovedNotification groupMembersRemoved;

    /**
     * 推送的ws消息
     */
    private ChatMessageResp msg;

    private Long roomId;
    /**
     * 推送的uid
     */
    private List<Long> uidList;

    /**
     * 推送类型 1个人 2全员
     */
    private SSEPushTypeEnum pushType;

    public PushMessageDTO(ChatMessageResp msg, List<Long> uidList, SSEPushTypeEnum pushType) {
        this.msg = msg;
        this.uidList = uidList;
        this.pushType = pushType;
    }

    public PushMessageDTO(FriendApplyNotification notification) {
        this.eventType = FriendApplyNotification.EVENT_TYPE;
        this.friendApply = notification;
        this.uidList = Collections.singletonList(notification.getTargetUid());
        this.pushType = SSEPushTypeEnum.USER;
    }

    public PushMessageDTO(GroupMembersRemovedNotification notification) {
        this.eventType = GroupMembersRemovedNotification.EVENT_TYPE;
        this.groupMembersRemoved = notification;
        this.roomId = notification.getRoomId();
        this.pushType = SSEPushTypeEnum.ROOM;
    }

    public PushMessageDTO(Long uid, ChatMessageResp msg) {
        this.uidList = Collections.singletonList(uid);
        this.msg = msg;
        this.pushType = SSEPushTypeEnum.USER;
    }

    public PushMessageDTO(List<Long> uidList, ChatMessageResp msg) {
        this.uidList = uidList;
        this.msg = msg;
        this.pushType = SSEPushTypeEnum.USER;
    }

    public static PushMessageDTO room(Long roomId, ChatMessageResp msg) {
        PushMessageDTO push = new PushMessageDTO();
        push.roomId = roomId;
        push.msg = msg;
        push.pushType = SSEPushTypeEnum.ROOM;
        return push;
    }

    public PushMessageDTO(ChatMessageResp msg) {
        this.msg = msg;
        this.pushType = SSEPushTypeEnum.ALL;
    }
}
