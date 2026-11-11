package com.openchat.chat.service.abstracts;

import com.openchat.chat.domain.dto.RoomMessage;
import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.domain.vo.req.ChatMessageReq;
import com.openchat.chat.domain.vo.resp.msg.TotalMsgResp;
import com.openchat.common.exception.BusinessException;
import org.springframework.stereotype.Component;

/** 系统提示只提供读取协议，写入必须由拥有对应业务事务的服务完成。 */
@Component
public class SystemMsgHandler extends AbstractMsgHandler<Object> {
    @Override
    MessageTypeEnum getMsgTypeEnum() {
        return MessageTypeEnum.SYSTEM;
    }

    @Override
    public void sendMsg(ChatMessageReq request, RoomMessage room, Long uid) {
        throw new BusinessException("系统消息只能由业务事件生成");
    }

    @Override
    public void checkAndSaveMsg(ChatMessageReq request, RoomMessage room, Long uid, Long msgId) {
        throw new BusinessException("系统消息只能由业务事件生成");
    }

    @Override
    public void checkMsg(Object body, Long roomId, Long uid, RoomTypeEnum roomType) {
        throw new BusinessException("系统消息只能由业务事件生成");
    }

    @Override
    public void fillMsgContent(Message message, Object body, RoomTypeEnum roomType) {
        throw new BusinessException("系统消息只能由业务事件生成");
    }

    @Override
    public Object showMsg(Message message) {
        return TotalMsgResp.builder().content(fullContent(message))
                .extra(systemExtra(message)).build();
    }

    @Override
    public Object showReplyMsg(Message message) {
        return fullContent(message);
    }

    @Override
    public String showContactMsg(Message message) {
        return message.getContent();
    }

    private String fullContent(Message message) {
        if (message.getExtra() == null || message.getExtra().getSystemContent() == null) {
            return message.getContent();
        }
        return message.getExtra().getSystemContent();
    }

    /** 返回当前系统消息的结构化快照。 */
    private Object systemExtra(Message message) {
        if (message.getExtra() == null) return null;
        if (message.getExtra().getGroupInvitation() != null) {
            return message.getExtra().getGroupInvitation();
        }
        return message.getExtra().getGroupRemoval();
    }
}
