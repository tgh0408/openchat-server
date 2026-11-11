package com.openchat.transaction.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.StaticSessionCredentialsProvider;
import org.apache.rocketmq.client.apis.consumer.LitePushConsumer;
import org.apache.rocketmq.client.apis.consumer.MessageListener;
import org.apache.rocketmq.client.apis.consumer.OffsetOption;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LiteTopic 的 SDK 适配及本节点资源生命周期；业务仅提供消费回调和订阅名称。
 * 本地互斥只协调客户端启动与关闭，不承担跨节点业务幂等或互斥。
 */
@Slf4j
@RequiredArgsConstructor
public class LiteTopicClient implements AutoCloseable {

    private final LiteTopicPushProperties properties;
    private final ClientServiceProvider provider;
    private final ScheduledExecutorService subscriptionExecutor;
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean starting = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private volatile Producer producer;
    private volatile LitePushConsumer consumer;

    /** 回调保持同步消费语义；连接成功后通知业务恢复本节点订阅。 */
    public synchronized void start(MessageListener messageListener, Runnable onReady) {
        if (!properties.isEnabled() || ready.get() || closed.get()
                || !starting.compareAndSet(false, true)) {
            return;
        }
        if (properties.getEndpoints() == null || properties.getEndpoints().isBlank()) {
            log.warn("LiteTopic is enabled but endpoints is empty");
            starting.set(false);
            return;
        }
        String nodeId = resolveNodeId();
        String consumerGroup = sanitize(properties.getConsumerGroup() + "-" + nodeId);
        try {
            ClientConfiguration configuration = buildClientConfiguration();
            log.info("Starting LiteTopic client, endpoints={}, ssl={}, parentTopic={}, consumerGroup={}",
                    properties.getEndpoints(), properties.isSsl(), properties.getParentTopic(), consumerGroup);
            producer = provider.newProducerBuilder()
                    .setClientConfiguration(configuration)
                    .setTopics(properties.getParentTopic())
                    .build();
            consumer = provider.newLitePushConsumerBuilder()
                    .setClientConfiguration(configuration)
                    .setConsumerGroup(consumerGroup)
                    .bindTopic(properties.getParentTopic())
                    .setMessageListener(messageListener)
                    .setConsumptionThreadCount(4)
                    .build();
            ready.set(true);
            onReady.run();
            log.info("LiteTopic client started, parentTopic={}, nodeId={}", properties.getParentTopic(), nodeId);
        } catch (Exception exception) {
            closeQuietly();
            log.error("Failed to start LiteTopic client. Verify Proxy connectivity and consumer group '{}'",
                    consumerGroup, exception);
            if (!closed.get()) {
                subscriptionExecutor.schedule(() -> start(messageListener, onReady), 10, TimeUnit.SECONDS);
            }
        } finally {
            starting.set(false);
        }
    }

    public boolean isReady() {
        return ready.get() && !closed.get();
    }

    public boolean isSubscribed(String liteTopic) {
        LitePushConsumer currentConsumer = consumer;
        return isReady() && currentConsumer != null && currentConsumer.getLiteTopicSet().contains(liteTopic);
    }

    /** 不可用时交由业务回退；实际发送失败仍抛出异常，由原消费链路重试。 */
    public boolean send(String liteTopic, String key, byte[] body) {
        Producer currentProducer = producer;
        if (!isReady() || currentProducer == null) {
            return false;
        }
        Message message = provider.newMessageBuilder()
                .setTopic(properties.getParentTopic())
                .setLiteTopic(liteTopic)
                .setKeys(key)
                .setBody(body)
                .build();
        try {
            SendReceipt receipt = currentProducer.send(message);
            log.debug("LiteTopic message sent, liteTopic={}, messageId={}", liteTopic, receipt.getMessageId());
            return true;
        } catch (ClientException exception) {
            throw new IllegalStateException("LiteTopic message send failed", exception);
        }
    }

    public boolean subscribe(String liteTopic) throws ClientException {
        LitePushConsumer currentConsumer = consumer;
        if (!isReady() || currentConsumer == null) {
            return false;
        }
        currentConsumer.subscribeLite(liteTopic, OffsetOption.LAST_OFFSET);
        return true;
    }

    public void unsubscribe(String liteTopic) throws ClientException {
        LitePushConsumer currentConsumer = consumer;
        if (currentConsumer != null && isSubscribed(liteTopic)) {
            currentConsumer.unsubscribeLite(liteTopic);
        }
    }

    /** 所有业务订阅任务继续使用同一串行调度器，关闭后不接收迟到任务。 */
    public void executeSubscription(Runnable task) {
        scheduleSubscription(task, 0, TimeUnit.SECONDS);
    }

    public void scheduleSubscription(Runnable task, long delay, TimeUnit unit) {
        if (!isReady()) {
            return;
        }
        try {
            subscriptionExecutor.schedule(() -> {
                if (isReady()) {
                    task.run();
                }
            }, delay, unit);
        } catch (RejectedExecutionException exception) {
            if (!closed.get()) {
                throw exception;
            }
        }
    }

    private ClientConfiguration buildClientConfiguration() {
        var builder = ClientConfiguration.newBuilder()
                .setEndpoints(properties.getEndpoints())
                .enableSsl(properties.isSsl());
        if (!properties.getAccessKey().isBlank() && !properties.getSecretKey().isBlank()) {
            builder.setCredentialProvider(
                    new StaticSessionCredentialsProvider(properties.getAccessKey(), properties.getSecretKey()));
        }
        return builder.build();
    }

    private String resolveNodeId() {
        if (properties.getNodeId() != null && !properties.getNodeId().isBlank()) {
            return sanitize(properties.getNodeId());
        }
        try {
            return sanitize(InetAddress.getLocalHost().getHostName()
                    + "-" + System.getProperty("server.port", "10086"));
        } catch (IOException exception) {
            return "node-" + Integer.toHexString(System.identityHashCode(this));
        }
    }

    private void closeQuietly() {
        ready.set(false);
        LitePushConsumer currentConsumer = consumer;
        try {
            if (currentConsumer != null) {
                try {
                    currentConsumer.close();
                } catch (IOException exception) {
                    log.warn("Failed to close LiteTopic consumer", exception);
                }
            }
        } finally {
            consumer = null;
            closeProducerQuietly();
        }
    }

    private void closeProducerQuietly() {
        Producer currentProducer = producer;
        try {
            if (currentProducer != null) {
                try {
                    currentProducer.close();
                } catch (IOException exception) {
                    log.warn("Failed to close LiteTopic producer", exception);
                }
            }
        } finally {
            producer = null;
        }
    }

    /** 提前关闭消费者，生产者保留到 Bean 销毁，避免中断尚未完成的发送调用。 */
    public synchronized void stopConsumer() {
        boolean firstStop = closed.compareAndSet(false, true);
        ready.set(false);
        try {
            if (consumer != null) {
                consumer.close();
                consumer = null;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to close LiteTopic consumer", exception);
        } finally {
            if (firstStop) {
                subscriptionExecutor.shutdownNow();
            }
        }
    }

    @Override
    public synchronized void close() {
        try {
            stopConsumer();
        } finally {
            closeProducerQuietly();
        }
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
