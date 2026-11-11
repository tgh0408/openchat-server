package com.openchat.common.event.listener;

import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.common.constant.MQConstant;
import com.openchat.common.domain.dto.MsgSendMessageDTO;
import com.openchat.common.event.GroupInfoUpdatedEvent;
import com.openchat.transaction.service.MQProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 同步登记提示投递，后续复用系统消息的跨节点群广播与历史补同步。 */
@Component
@RequiredArgsConstructor
public class GroupInfoUpdatedListener {
    private final MQProducer mqProducer;

    @EventListener
    public void publish(GroupInfoUpdatedEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("群资料修改提示必须在业务事务内登记");
        }
        if (event.messageId() == null || event.messageId() <= 0) {
            throw new IllegalArgumentException("无效的群资料修改提示消息 ID");
        }
        mqProducer.sendSecureMsg(MQConstant.SEND_MSG_TOPIC,
                new MsgSendMessageDTO(event.messageId(), RoomTypeEnum.GROUP), event.messageId());
    }
}
