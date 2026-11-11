package com.openchat.chat.consumer;

import com.openchat.chat.domain.dto.SaveMsgArg;
import com.openchat.chat.service.ChatService;
import com.openchat.oss.OssExceptionDiagnostics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openchat.transaction.mq.MQLocalTransactionListener;
import org.apache.rocketmq.client.apis.producer.TransactionResolution;
import org.springframework.stereotype.Component;

/**
 * 发送消息事务监听器：半消息 → 本地事务落库 → commit/rollback
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MsgTransactionListener implements MQLocalTransactionListener {
    final ChatService chatService;

    @Override
    public TransactionResolution executeLocalTransaction(String key, Object arg) {
        try {
            SaveMsgArg saveMsgArg = (SaveMsgArg) arg;
            chatService.saveMsg(saveMsgArg);
            return TransactionResolution.COMMIT;
        } catch (Exception e) {
            log.error("消息事务落库失败, msgId={}", key, OssExceptionDiagnostics.sanitized(e));
            return TransactionResolution.ROLLBACK;
        }
    }

    @Override
    public TransactionResolution checkLocalTransaction(String key) {
        Long msgId = key == null ? null : Long.valueOf(key);
        if (msgId == null) {
            return TransactionResolution.ROLLBACK;
        }
        if (chatService.isMsgExist(msgId)) {
            return TransactionResolution.COMMIT;
        }
        // 回查可能早于本地事务提交，未查到不能证明已经回滚。
        log.warn("消息事务回查暂未查到提交结果, msgId={}", msgId);
        return TransactionResolution.UNKNOWN;
    }
}
