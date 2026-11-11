package com.openchat.common.event.listener;

import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.common.constant.MQConstant;
import com.openchat.common.domain.dto.MsgSendMessageDTO;
import com.openchat.common.event.GroupRemovalMessageEvent;
import com.openchat.transaction.service.MQProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 移出提示沿用现有可靠调用及群广播，不维护单独的离线通知或送达确认。 */
@Component
@RequiredArgsConstructor
public class GroupRemovalMessageListener {
    private final MQProducer mqProducer;

    @EventListener
    public void publish(GroupRemovalMessageEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("移出群聊提示必须在业务事务内登记");
        }
        if (event.messageId() == null || event.messageId() <= 0) {
            throw new IllegalArgumentException("无效的移出群聊提示消息 ID");
        }
        mqProducer.sendSecureMsg(
                MQConstant.SEND_MSG_TOPIC,
                new MsgSendMessageDTO(event.messageId(), RoomTypeEnum.GROUP),
                event.messageId());
    }
}
