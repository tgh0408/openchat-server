package com.openchat.chat.service.adapter;

import cn.hutool.core.bean.BeanUtil;
import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.MessageMark;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.vo.req.ChatMessageReq;
import com.openchat.chat.domain.vo.req.msg.TextMsgReq;
import com.openchat.chat.domain.vo.resp.ChatMessageResp;
import com.openchat.chat.service.abstracts.AbstractMsgHandler;
import com.openchat.chat.service.abstracts.MsgHandlerFactory;
import com.openchat.common.domain.enums.YesOrNoEnum;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Description: 消息适配器
 * Author:
 * Date: 2023-03-26
 */
public class MessageAdapter {
    public static final int CAN_CALLBACK_GAP_COUNT = 100;

    public static Message buildMsgSave(ChatMessageReq request, Long uid) {
        return Message.builder()
                .fromUid(uid)
                .roomId(request.getRoomId())
                .type(request.getMsgType())
                .status(YesOrNoEnum.NO)
                .build();
    }

    public static List<ChatMessageResp> buildMsgResp(List<Message> messages, List<MessageMark> msgMark, Long receiveUid) {
        Map<Long, List<MessageMark>> markMap = msgMark.stream().collect(Collectors.groupingBy(MessageMark::getMsgId));
        return messages.stream().map(a -> {
                    ChatMessageResp resp = new ChatMessageResp();
                    resp.setFromUser(buildFromUser(a.getFromUid()));
                    resp.setMessage(buildMessage(a));
                    return resp;
                })
                .sorted(Comparator.comparing(a -> a.getMessage().getSendTime()))//帮前端排好序，更方便它展示
                .collect(Collectors.toList());
    }

    private static ChatMessageResp.Message buildMessage(Message message) {
        ChatMessageResp.Message messageVO = new ChatMessageResp.Message();
        BeanUtil.copyProperties(message, messageVO);
        messageVO.setReplyId(message.getReplyMsgId());
        messageVO.setSendTime(message.getCreateTime());
        AbstractMsgHandler<?> msgHandler = MsgHandlerFactory.getStrategyNoNull(message.getType());
        messageVO.setBody(msgHandler.showMsg(message));
        return messageVO;
    }


    private static ChatMessageResp.UserInfo buildFromUser(Long fromUid) {
        ChatMessageResp.UserInfo userInfo = new ChatMessageResp.UserInfo();
        userInfo.setUid(fromUid);
        return userInfo;
    }

    public static ChatMessageReq buildAgreeMsg(Long roomId) {
        ChatMessageReq chatMessageReq = new ChatMessageReq();
        chatMessageReq.setRoomId(roomId);
        chatMessageReq.setMsgType(MessageTypeEnum.TEXT);
        TextMsgReq textMsgReq = new TextMsgReq();
        textMsgReq.setContent("我们已经成为好友了，开始聊天吧");
        chatMessageReq.setBody(textMsgReq);
        return chatMessageReq;
    }
}
