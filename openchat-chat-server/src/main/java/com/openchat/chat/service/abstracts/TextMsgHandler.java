package com.openchat.chat.service.abstracts;

import com.openchat.chat.domain.entity.Message;
import com.openchat.chat.domain.entity.msg.MessageExtra;
import com.openchat.chat.domain.enums.MessageTypeEnum;
import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.chat.domain.vo.req.msg.TextMsgReq;
import com.openchat.chat.domain.vo.resp.msg.TotalMsgResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TextMsgHandler extends AbstractMsgHandler<TextMsgReq> {

    @Override
    MessageTypeEnum getMsgTypeEnum() {
        return MessageTypeEnum.TEXT;
    }

    @Override
    public void checkMsg(TextMsgReq body, Long roomId, Long uid, RoomTypeEnum roomType) {
    }

    @Override
    public void fillMsgContent(Message message, TextMsgReq body, RoomTypeEnum roomTypeEnum) {
        MessageExtra extra = Optional.ofNullable(message.getExtra()).orElse(new MessageExtra());
        message.setExtra(extra);
        message.setContent(body.getContent());
    }


    @Override
    public Object showMsg(Message msg) {
        TotalMsgResp resp = new TotalMsgResp();
        resp.setContent(msg.getContent());
        resp.setUrlContentMap(Optional.ofNullable(msg.getExtra()).map(MessageExtra::getUrlContentMap).orElse(null));
        resp.setAtUidList(Optional.ofNullable(msg.getExtra()).map(MessageExtra::getAtUidList).orElse(null));
        return resp;
    }

    @Override
    public Object showReplyMsg(Message msg) {
        return msg.getContent();
    }

    @Override
    public String showContactMsg(Message msg) {
        return msg.getContent();
    }
}
