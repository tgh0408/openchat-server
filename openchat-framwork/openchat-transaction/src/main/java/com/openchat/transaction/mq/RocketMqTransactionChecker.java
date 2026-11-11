package com.openchat.transaction.mq;

import com.openchat.transaction.service.MQProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.apis.producer.TransactionResolution;
import org.apache.rocketmq.client.core.RocketMQTransactionChecker;
import org.springframework.beans.factory.ObjectProvider;

/** 使用 V5 标准事务回查注册；延迟取得业务入口，避免发送服务的循环依赖。 */
@Slf4j
@RocketMQTransactionListener
@RequiredArgsConstructor
public class RocketMqTransactionChecker implements RocketMQTransactionChecker {

    private final ObjectProvider<MQLocalTransactionListener> listener;

    @Override
    public TransactionResolution check(MessageView message) {
        try {
            if (message.getProperties().containsKey("openchat_tx_record_id")) {
                // 旧发布快照协议必须在升级前由旧版本排空，不能误按聊天消息确认。
                return TransactionResolution.UNKNOWN;
            }
            if (message.getProperties().containsKey(MQProducer.COMMITTED_NOTIFICATION_HEADER)) {
                // 此标记只能由提交后通知入口设置，半消息产生之前业务已完成。
                return "true".equals(message.getProperties().get(MQProducer.COMMITTED_NOTIFICATION_HEADER))
                        ? TransactionResolution.COMMIT : TransactionResolution.UNKNOWN;
            }
            if (message.getKeys().size() != 1) {
                return TransactionResolution.UNKNOWN;
            }
            return listener.getObject().checkLocalTransaction(message.getKeys().iterator().next());
        } catch (RuntimeException exception) {
            // 查询失败不是未落库，保留半消息等待后续回查。
            log.error("RocketMQ transaction check failed, messageId={}", message.getMessageId(), exception);
            return TransactionResolution.UNKNOWN;
        }
    }
}
