package com.openchat.transaction.mq;

import org.apache.rocketmq.client.apis.producer.TransactionResolution;

/** 本地事务与回查的业务入口；key 沿用发送时的消息标识。 */
public interface MQLocalTransactionListener {

    TransactionResolution executeLocalTransaction(String key, Object arg);

    TransactionResolution checkLocalTransaction(String key);
}
