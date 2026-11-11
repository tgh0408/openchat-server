package com.openchat.transaction.service;

import com.openchat.transaction.annotation.SecureInvoke;
import com.openchat.transaction.mq.MQLocalTransactionListener;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.producer.Transaction;
import org.apache.rocketmq.client.apis.producer.TransactionResolution;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;
import tools.jackson.databind.json.JsonMapper;

/**
 * Description: 发送mq工具类
 * Author: 
 * Date: 2023-08-12
 */
public class MQProducer {

    /** 仅用于业务提交后发布的通知，不能用于尚待执行本地事务的新聊天消息。 */
    public static final String COMMITTED_NOTIFICATION_HEADER = "openchat_committed_notification";

    @Autowired
    private RocketMQClientTemplate rocketMQTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ObjectProvider<MQLocalTransactionListener> transactionListener;

    /**
     * 发布意图与当前业务事务一起提交，发送失败由既有可靠调用任务重试。
     *
     * @param topic 主题
     * @param body  消息体
     */
    @SecureInvoke
    public void sendSecureMsg(String topic, Object body, Object key) {
        String businessKey = key == null ? null : String.valueOf(key);
        sendNotification(topic, body, businessKey == null || businessKey.isBlank() ? null : businessKey);
    }

    @SecureInvoke
    public void sendSecureMsg(String topic, Object body) {
        sendSecureMsg(topic, body, null);
    }

    /** 回放中的嵌套业务事务也必须提交后才能发布，不能提前给半消息标记已提交。 */
    private void sendNotification(String topic, Object body, String key) {
        Assert.hasText(topic, "RocketMQ topic must not be empty");
        Message<String> message = MessageBuilder.withPayload(jsonMapper.writeValueAsString(body))
                .setHeader("KEYS", key)
                .setHeader(COMMITTED_NOTIFICATION_HEADER, "true")
                .build();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishNotification(topic, message);
                }
            });
            return;
        }
        publishNotification(topic, message);
    }

    /** 到达此处不再有待执行的业务事务；半消息持久化后，即使节点中断也可回查提交。 */
    private void publishNotification(String topic, Message<String> message) {
        Transaction transaction = rocketMQTemplate.sendTransactionMessage(topic, message).getTransaction();
        resolve(transaction, TransactionResolution.COMMIT);
    }

    /**
     * 发送事务消息（半消息）
     *
     * @param topic 主题
     * @param body  消息体
     * @param key   消息 key（msgId，供回查定位）
     * @param arg   本地事务参数
     * @return 本地事务决议；若加入外层事务，COMMIT 仍需等待该事务实际提交
     */
    public TransactionResolution sendTransactionMsg(String topic, Object body, Object key, Object arg) {
        Message<String> build = MessageBuilder
                .withPayload(jsonMapper.writeValueAsString(body))
                .setHeader("KEYS", key)
                .build();
        MQLocalTransactionListener listener = transactionListener.getObject();
        Transaction transaction = rocketMQTemplate.sendTransactionMessage(topic, build).getTransaction();
        var resolution = listener.executeLocalTransaction(String.valueOf(key), arg);
        if (resolution == TransactionResolution.COMMIT
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            // 本地回调可能加入好友审批等外层事务，方法返回不代表数据库已经提交。
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    switch (status) {
                        case STATUS_COMMITTED -> resolve(transaction, TransactionResolution.COMMIT);
                        case STATUS_ROLLED_BACK -> resolve(transaction, TransactionResolution.ROLLBACK);
                        default -> { /* 提交结果不确定，由回查处理。 */ }
                    }
                }
            });
            return resolution;
        }
        resolve(transaction, resolution);
        return resolution;
    }

    /** 确认失败保留半消息，按业务落库或已提交通知标记回查，不能反向回滚已提交业务。 */
    private void resolve(Transaction transaction, TransactionResolution resolution) {
        try {
            switch (resolution) {
                case COMMIT -> transaction.commit();
                case ROLLBACK -> transaction.rollback();
                case UNKNOWN -> { /* 保留半消息，由业务回查决定最终状态。 */ }
            }
        } catch (ClientException exception) {
            // 本地事务可能已提交，MQ 确认失败不能反向回滚；后续回查恢复。
            throw new MessagingException("RocketMQ transaction acknowledgement failed", exception);
        }
    }
}
