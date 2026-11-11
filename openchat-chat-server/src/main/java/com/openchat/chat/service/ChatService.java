package com.openchat.chat.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.openchat.chat.dao.ContactDao;
import com.openchat.chat.dao.MessageDao;
import com.openchat.chat.dao.TemporaryMessageDao;
import com.openchat.chat.domain.dto.RoomMessage;
import com.openchat.chat.domain.dto.SaveMsgArg;
import com.openchat.chat.domain.entity.Contact;
import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.Room;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.domain.vo.req.ChatMessagePageReq;
import com.openchat.chat.domain.vo.req.ChatMessageReq;
import com.openchat.chat.domain.vo.req.TemporaryStateReq;
import com.openchat.chat.domain.vo.resp.ChatMessageResp;
import com.openchat.chat.service.abstracts.AbstractMsgHandler;
import com.openchat.chat.service.abstracts.MsgHandlerFactory;
import com.openchat.chat.service.adapter.MessageAdapter;
import com.openchat.common.domain.enums.MsgSourceEnum;
import com.openchat.common.domain.enums.YesOrNoEnum;
import com.openchat.common.domain.vo.resp.CursorPageBaseResp;
import com.openchat.common.exception.BusinessException;
import com.openchat.common.utils.AssertUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ChatService {
    final MessageDao messageDao;
    final TemporaryMessageDao temporaryMessageDao;
    final ContactDao contactDao;
    private final RoomAccessService roomAccessService;

    public void sendMsg(ChatMessageReq request, Long uid, MsgSourceEnum source) {
        AssertUtil.isNonNull(source, "消息来源不能为空");
        MessageTypeEnum messageType = request.getMsgType();
        switch (source) {
            case CLIENT -> {
                if (messageType == MessageTypeEnum.SYSTEM) {
                    throw new BusinessException("系统消息只能由业务事件生成");
                }
                if (messageType == MessageTypeEnum.RTC_CALL) {
                    throw new BusinessException("RTC 通话摘要只能由服务端生成");
                }
            }
            case SERVER -> {
                if (messageType != MessageTypeEnum.RTC_CALL) {
                    throw new BusinessException("当前仅支持服务端发送 RTC 通话摘要");
                }
            }
        }
        //检查信息，是否在为好友或者在群聊中
        RoomMessage roomMessage = checkAndGetRoomFriend(request, uid);
        MsgHandlerFactory.getStrategyNoNull(request.getMsgType())
                .sendMsg(request, roomMessage, uid);
    }

    /**
     * 事务消息本地事务：落库消息。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveMsg(SaveMsgArg arg) {
        RoomMessage roomMessage = checkAndGetRoomFriend(arg.getRequest(), arg.getUid());
        AbstractMsgHandler<?> msgHandler = MsgHandlerFactory.getStrategyNoNull(arg.getRequest().getMsgType());
        msgHandler.checkAndSaveMsg(arg.getRequest(), roomMessage, arg.getUid(), arg.getMsgId());
    }

    /**
     * 事务消息回查：消息是否已落库。
     */
    public boolean isMsgExist(Long msgId) {
        return messageDao.exists(msgId);
    }

    /**
     * 组装前端消息，receiveUid 可为空。
     */
    public ChatMessageResp getMsgResp(Message message, Long receiveUid) {
        return CollUtil.getFirst(getMsgRespBatch(Collections.singletonList(message), receiveUid));
    }

    public CursorPageBaseResp<ChatMessageResp> getMsgPage(ChatMessagePageReq request, Long receiveUid) {
        Room room = roomAccessService.requireCurrentMember(receiveUid, request.getRoomId());
        Long roomId = room.getId();
        Long lastMsgId = Optional.ofNullable(room.getLastMsgId()).orElse(0L);
        Contact contact = contactDao.getContactStatus(roomId, receiveUid);
        Long lastDeleteId = contact == null ? 0L : contact.getLastDeleteId();
        if (room.getType() == RoomTypeEnum.GROUP) {
            CursorPageBaseResp<Message> cursorPage =
                    messageDao.getCursorPage(RoomTypeEnum.GROUP, roomId, request, lastMsgId, lastDeleteId);
            if (cursorPage == null || cursorPage.isEmpty()) {
                return CursorPageBaseResp.empty();
            }
            return CursorPageBaseResp.init(cursorPage, getMsgRespBatch(cursorPage.getList(), receiveUid));
        } else if (room.getType().usesUserMessageTable()) {
            // 隐藏会话仍需保留历史删除边界。
            CursorPageBaseResp<Message> cursorPage =
                    messageDao.getCursorPage(room.getType(), roomId, request, lastMsgId, lastDeleteId);
            if (cursorPage == null || cursorPage.isEmpty()) {
                return CursorPageBaseResp.empty();
            }
            return CursorPageBaseResp.init(cursorPage, getMsgRespBatch(cursorPage.getList(), receiveUid));
        }
        return CursorPageBaseResp.empty();
    }

    private RoomMessage checkAndGetRoomFriend(ChatMessageReq request, Long uid) {
        if (uid == null || uid <= 0) {
            throw new BusinessException("请先登录");
        }
        if (request.getRoomId() == null || request.getRoomId() <= 0) {
            throw new BusinessException("无效的房间 ID");
        }
        //校验房间是否存在
        // 撤回只清理已有消息，继续由处理器校验所有权和时限，不被发送黑名单阻断。
        Room room = request.getMsgType() == MessageTypeEnum.RECALL
                ? roomAccessService.requireCurrentMember(uid, request.getRoomId())
                : roomAccessService.requireCanSend(uid, request.getRoomId());

        //首先判断是群聊还是单聊还是文件助手
        if (room.getType() == RoomTypeEnum.FILE_ASSISTANT) {
            return assistantRoom(roomAccessService.requireCurrentMember(uid, room.getId()), uid);
        } else if (room.getType() == RoomTypeEnum.FRIEND) {
            Long peer = roomAccessService.requireFriendPeer(uid, request.getRoomId());
            return RoomMessage.builder()
                    .roomId(request.getRoomId())
                    .roomType(RoomTypeEnum.FRIEND)
                    .userId(uid)
                    .friendId(peer)
                    .build();
        } else if (room.getType() == RoomTypeEnum.GROUP) {
            // 当前成员资格已由 RoomAccessService 查询数据库，不再用旧缓存二次覆盖结果。
            return RoomMessage.builder()
                    .roomId(request.getRoomId())
                    .roomType(RoomTypeEnum.GROUP)
                    .userId(uid)
                    .build();
        }
        throw new BusinessException("无法判断房间类型，请联系管理员");
    }

    /**
     * 重连核对已有缓存的原位更新；不依赖房间历史上界、不推进已读，也不读取旧消息缓存。
     */
    public List<ChatMessageResp> getMsgState(TemporaryStateReq request, Long uid) {
        return getMsgState(request, uid, false);
    }

    public List<ChatMessageResp> getTemporaryMsgState(TemporaryStateReq request, Long uid) {
        return getMsgState(request, uid, true);
    }

    private List<ChatMessageResp> getMsgState(TemporaryStateReq request, Long uid, boolean uploadOnly) {
        Room room = roomAccessService.requireCurrentMember(uid, request.getRoomId());
        RoomTypeEnum type = room.getType();
        Contact contact = type.usesUserMessageTable() ? contactDao.getContactStatus(room.getId(), uid) : null;
        long boundary = contact == null || contact.getLastDeleteId() == null ? 0L : contact.getLastDeleteId();
        List<Message> found = uploadOnly
                ? temporaryMessageDao.getUploadStates(type, room.getId(), request.getMsgIds())
                : messageDao.listByIds(type, request.getMsgIds());
        List<Message> visible = new ArrayList<>();
        for (Message message : found) {
            if (Objects.equals(message.getRoomId(), room.getId()) && message.getStatus() == YesOrNoEnum.NO
                    && message.getId() > boundary
                    && (!uploadOnly || MessageTypeEnum.equalsIn(
                    message.getType(), MessageTypeEnum.TEMP, MessageTypeEnum.VIDEO,
                    MessageTypeEnum.FILE, MessageTypeEnum.RECALL))) {
                visible.add(message);
            }
        }
        return getMsgRespBatch(visible, uid);
    }

    private RoomMessage assistantRoom(Room room, Long uid) {
        return RoomMessage.builder().roomId(room.getId()).roomType(RoomTypeEnum.FILE_ASSISTANT)
                .userId(uid).friendId(uid).build();
    }

    public List<ChatMessageResp> getMsgRespBatch(List<Message> messages, Long receiveUid) {
        if (CollectionUtil.isEmpty(messages)) {
            return new ArrayList<>();
        }
        //查询消息标志
        //List<MessageMark> msgMark = messageMarkDao.getValidMarkByMsgIdBatch(messages.stream().map(Message::getId).collect(Collectors.toList()));
        return MessageAdapter.buildMsgResp(messages, List.of(), receiveUid);
    }
}
