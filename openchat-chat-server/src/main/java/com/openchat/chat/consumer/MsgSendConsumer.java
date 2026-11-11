package com.openchat.chat.consumer;

import com.openchat.chat.dao.*;
import com.openchat.chat.domain.dto.PushMessageDTO;
import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.domain.vo.resp.ChatMessageResp;
import com.openchat.chat.service.ChatService;
import com.openchat.chat.service.RoomActivityService;
import com.openchat.chat.service.RoomAccessService;
import com.openchat.chat.dao.GroupMemberDao;
import com.openchat.common.rocketmq.AbstractRocketQListener;
import com.openchat.common.utils.AssertUtil;
import com.openchat.user.dao.RoomFriendDao;
import com.openchat.chat.dao.TemporaryMessageDao;
import com.openchat.common.constant.MQConstant;
import com.openchat.common.domain.dto.MsgSendMessageDTO;
import com.openchat.transaction.service.MQProducer;
import com.openchat.user.domain.entity.RoomFriend;
import com.openchat.user.domain.enums.SSEPushTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Description: 发送消息更新房间收信箱，并同步给房间成员信箱
 * Author:
 * Date: 2023-08-12
 */
@Component
@RocketMQMessageListener(
        topic = MQConstant.SEND_MSG_TOPIC,
        consumerGroup = MQConstant.SEND_MSG_GROUP,
        endpoints = "${rocketmq.producer.endpoints}",
        accessKey = "${rocketmq.producer.access-key:}",
        secretKey = "${rocketmq.producer.secret-key:}",
        namespace = "${rocketmq.producer.namespace:}")
@RequiredArgsConstructor
@Slf4j
public class MsgSendConsumer extends AbstractRocketQListener<MsgSendMessageDTO> {
    final ChatService chatService;
    final MessageDao messageDao;
    final RoomDao roomDao;
    final RoomActivityService roomActivityService;
    final RoomFriendDao roomFriendDao;
    final ContactDao contactDao;
    final MQProducer mqProducer;
    final GroupMemberDao groupMemberDao;
    private final RoomAccessService roomAccessService;
    private final TemporaryMessageDao temporaryMessageDao;
    private final LiteTopicPush liteTopicPush;

    // 群消息与个人消息分开,但是个人消息还是会存一份在群消息中(总消息表)
    protected void onMessage(MsgSendMessageDTO dto) {
        RoomTypeEnum roomTypeEnum = dto.getRoomType();
        Message message = messageDao.getById(roomTypeEnum, dto.getMsgId());
        AssertUtil.isNonNull(message, "待推送的消息不存在");

        Room room = roomDao.get(message.getRoomId(), "消息房间不存在");
        AssertUtil.isTrue(room.getType() == roomTypeEnum, "消息类型不匹配");

        if (roomTypeEnum == RoomTypeEnum.FILE_ASSISTANT) {
            room = roomAccessService.requireCurrentMember(message.getFromUid(), message.getRoomId());
        }
        if (dto.isUpdate() && (TemporaryMessageDao.supports(message.getType())
                || message.getType() == MessageTypeEnum.RECALL))
            temporaryMessageDao.invalidateMedia(roomTypeEnum, room.getId(), message.getId());
        ChatMessageResp msgResp = chatService.getMsgResp(message, null);
        boolean systemMessage = message.getType() == MessageTypeEnum.SYSTEM;
        //所有房间更新房间最新消息
        if (!dto.isUpdate()) {
            roomActivityService.record(room.getId(), message.getId(), message.getCreateTime());
            //能发消息说明我正在当前会话,清除未读数
            if (!systemMessage) contactDao.markRead(message.getFromUid(), room.getId(), message.getId());
        }
        PushMessageDTO pushMessage;
        if (roomTypeEnum == RoomTypeEnum.GROUP) {//普通群聊推送所有群成员
            if (room.isHotRoom() || systemMessage) {
                pushMessage = PushMessageDTO.room(room.getId(), msgResp);
            } else {
                List<String> memberUids = groupMemberDao.getMemberUids(room.getId());
                pushMessage = new PushMessageDTO(
                        memberUids.stream().map(Long::parseLong).toList(), msgResp);
            }
        } else if (roomTypeEnum == RoomTypeEnum.FRIEND) {//单聊对象
            //对单人推送
            RoomFriend roomFriend = roomFriendDao.get(room.getId());
            pushMessage = new PushMessageDTO(
                    Arrays.asList(roomFriend.getUid1(), roomFriend.getUid2()), msgResp);
        } else if (roomTypeEnum == RoomTypeEnum.FILE_ASSISTANT) {
            // 仅同步到所属用户的在线连接，不查询好友或群成员。
            pushMessage = new PushMessageDTO(Collections.singletonList(room.getOwnerUid()), msgResp);
        }else {
            pushMessage = new PushMessageDTO(Collections.emptyList(), msgResp);
        }
        //更新所有群成员的会话时间

        /**
         * @see com.openchat.user.consumer.PushConsumer
         * @see com.openchat.chat.consumer.LiteTopicPush
         */

        //推送房间成员(超过指定人数的群聊使用)
        // 新成员所在节点可能尚未订阅热群；入群提示通过广播到达所有 SSE 节点。
        if (!systemMessage && SSEPushTypeEnum.ROOM == pushMessage.getPushType()
                && liteTopicPush.send(pushMessage)) {
            return;
        }
        mqProducer.sendSecureMsg(MQConstant.SSE_TOPIC, pushMessage);
    }


}
