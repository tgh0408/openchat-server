package com.openchat.common.event.listener;

import com.openchat.chat.domain.dto.PushMessageDTO;
import com.openchat.common.constant.MQConstant;
import com.openchat.common.event.FriendApplyChangedEvent;
import com.openchat.common.event.UserApplyEvent;
import com.openchat.transaction.service.MQProducer;
import com.openchat.user.domain.vo.resp.FriendApplyNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserApplyListener {
    final MQProducer mqProducer;

    // 同步监听才能把可靠调用记录写入原事务，不能在 AFTER_COMMIT 阶段才登记。
    @EventListener
    public void notifyFriend(UserApplyEvent event) {
        sendFriendApplyChanged(event.getUserApply().getTargetId());
    }

    @EventListener
    public void notifyChanged(FriendApplyChangedEvent event) {
        sendFriendApplyChanged(event.getUid());
    }

    /** 组装好友申请通知，可靠调用记录与申请变更在同一事务提交。 */
    private void sendFriendApplyChanged(Long uid) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("好友申请通知必须在业务事务中登记");
        }
        mqProducer.sendSecureMsg(MQConstant.SSE_TOPIC,
                new PushMessageDTO(new FriendApplyNotification(uid)), "friend-apply:" + uid);
    }
}
