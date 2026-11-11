package com.openchat.common.event.listener;

import com.openchat.chat.domain.dto.PushMessageDTO;
import com.openchat.chat.domain.vo.resp.GroupMembersRemovedNotification;
import com.openchat.common.constant.MQConstant;
import com.openchat.common.event.GroupMembersRemovedEvent;
import com.openchat.transaction.service.MQProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 可靠调用记录与成员删除同事务提交，广播由各节点按当前成员关系投递。 */
@Component
@RequiredArgsConstructor
public class GroupMembersRemovedListener {
    private final MQProducer mqProducer;

    @EventListener
    public void publish(GroupMembersRemovedEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("移出成员通知必须在业务事务内登记");
        }
        if (event.roomId() == null || event.roomId() <= 0 || event.removedUids().isEmpty()
                || event.removedUids().size() > 100
                || event.removedUids().stream().anyMatch(uid -> uid == null || uid <= 0)) {
            throw new IllegalArgumentException("无效的移出成员通知");
        }
        mqProducer.sendSecureMsg(MQConstant.SSE_TOPIC,
                new PushMessageDTO(new GroupMembersRemovedNotification(event.roomId(), event.removedUids())),
                "group-members-removed:" + event.roomId());
    }
}
