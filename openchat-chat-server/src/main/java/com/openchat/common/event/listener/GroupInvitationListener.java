package com.openchat.common.event.listener;

import com.openchat.chat.domain.enums.RoomTypeEnum;
import com.openchat.common.constant.MQConstant;
import com.openchat.common.domain.dto.MsgSendMessageDTO;
import com.openchat.common.event.GroupInvitationEvent;
import com.openchat.transaction.service.MQProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class GroupInvitationListener {
    private final MQProducer mqProducer;

    /** 同步登记可靠调用，与入群事务一起提交，不能使用异步或 AFTER_COMMIT 监听。 */
    @EventListener
    public void publish(GroupInvitationEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("入群提示必须在业务事务中登记");
        }
        if (event.messageId() == null || event.messageId() <= 0) {
            throw new IllegalArgumentException("无效的入群提示消息 ID");
        }
        mqProducer.sendSecureMsg(MQConstant.SEND_MSG_TOPIC,
                new MsgSendMessageDTO(event.messageId(), RoomTypeEnum.GROUP), event.messageId());
    }

}
