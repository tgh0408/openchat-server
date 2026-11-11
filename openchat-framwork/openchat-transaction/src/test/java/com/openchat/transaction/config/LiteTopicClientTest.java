package com.openchat.transaction.config;

import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.LitePushConsumer;
import org.apache.rocketmq.client.apis.consumer.LitePushConsumerBuilder;
import org.apache.rocketmq.client.apis.consumer.MessageListener;
import org.apache.rocketmq.client.apis.consumer.OffsetOption;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.message.MessageBuilder;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.ProducerBuilder;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LiteTopicClientTest {

    @Test
    public void preservesConnectionSettingsAndSynchronousConsumerCallback() throws Exception {
        var fixture = new Fixture("node / 1");
        fixture.properties.setSsl(true);
        fixture.properties.setAccessKey("test-access");
        fixture.properties.setSecretKey("test-secret");
        var listener = mock(MessageListener.class);
        var onReady = mock(Runnable.class);
        try (var client = fixture.client) {
            client.start(listener, onReady);
            assertTrue(client.isReady());
            verify(onReady).run();
            verify(fixture.consumerBuilder).setMessageListener(listener);
            verify(fixture.consumerBuilder).setConsumerGroup("test-group-node___1");
            verify(fixture.consumerBuilder).bindTopic("test-parent");
            verify(fixture.consumerBuilder).setConsumptionThreadCount(4);
            var configuration = ArgumentCaptor.forClass(ClientConfiguration.class);
            verify(fixture.consumerBuilder).setClientConfiguration(configuration.capture());
            assertEquals("127.0.0.1:1", configuration.getValue().getEndpoints());
            assertTrue(configuration.getValue().isSslEnabled());
            assertTrue(configuration.getValue().getCredentialsProvider().isPresent());
            verify(fixture.producerBuilder).setClientConfiguration(configuration.getValue());
        }
    }

    @Test
    public void partialStartupFailureClosesResourcesAndRetriesSameCallbacks() throws Exception {
        var fixture = new Fixture("node-1");
        var failedProducer = mock(Producer.class);
        when(fixture.producerBuilder.build()).thenReturn(failedProducer, fixture.producer);
        when(fixture.consumerBuilder.build())
                .thenThrow(new ClientException("expected startup failure"))
                .thenReturn(fixture.consumer);
        var listener = mock(MessageListener.class);
        var onReady = mock(Runnable.class);
        try (var client = fixture.client) {
            client.start(listener, onReady);
            assertFalse(client.isReady());
            verify(failedProducer).close();
            verifyNoInteractions(onReady);
            var retry = ArgumentCaptor.forClass(Runnable.class);
            verify(fixture.scheduler).schedule(retry.capture(), eq(10L), eq(TimeUnit.SECONDS));
            retry.getValue().run();
            assertTrue(client.isReady());
            verify(onReady).run();
            verify(fixture.consumerBuilder, times(2)).setMessageListener(listener);
            client.stopConsumer();
            retry.getValue().run();
            verify(fixture.consumerBuilder, times(2)).build();
        }
    }

    @Test
    public void preservesMessageMetadataSubscriptionOffsetsAndSendFailure() throws Exception {
        var fixture = new Fixture("node-1");
        var builder = mock(MessageBuilder.class, RETURNS_SELF);
        var message = mock(Message.class);
        when(fixture.provider.newMessageBuilder()).thenReturn(builder);
        when(builder.build()).thenReturn(message);
        when(fixture.producer.send(message)).thenReturn(mock(SendReceipt.class));
        byte[] body = new byte[] {1, 2, 3};
        try (var client = fixture.client) {
            fixture.start();
            assertTrue(client.subscribe("room-42"));
            verify(fixture.consumer).subscribeLite("room-42", OffsetOption.LAST_OFFSET);
            when(fixture.consumer.getLiteTopicSet()).thenReturn(Set.of("room-42"));
            assertTrue(client.isSubscribed("room-42"));
            assertTrue(client.send("room-42", "9007199254740993", body));
            verify(builder).setTopic("test-parent");
            verify(builder).setLiteTopic("room-42");
            verify(builder).setKeys("9007199254740993");
            verify(builder).setBody(body);
            client.unsubscribe("room-42");
            verify(fixture.consumer).unsubscribeLite("room-42");
            when(fixture.producer.send(message)).thenThrow(new ClientException("expected send failure"));
            assertThrows(IllegalStateException.class, () -> client.send("room-42", "key", body));
            client.stopConsumer();
            assertFalse(client.send("room-42", "key", body));
            assertFalse(client.subscribe("room-42"));
            assertFalse(client.isSubscribed("room-42"));
        }
    }

    @Test
    public void lateSubscriptionTaskDoesNotRunAfterStop() throws Exception {
        var fixture = new Fixture("node-1");
        var task = mock(Runnable.class);
        try (var client = fixture.client) {
            fixture.start();
            client.scheduleSubscription(task, 2, TimeUnit.SECONDS);
            var scheduled = ArgumentCaptor.forClass(Runnable.class);
            verify(fixture.scheduler).schedule(scheduled.capture(), eq(2L), eq(TimeUnit.SECONDS));
            client.stopConsumer();
            scheduled.getValue().run();
            client.executeSubscription(task);
            verifyNoInteractions(task);
            verify(fixture.scheduler, times(1)).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        }
    }

    @Test
    public void schedulerRejectionIsOnlyIgnoredWhenShutdownHasStarted() throws Exception {
        var fixture = new Fixture("node-1");
        try (var client = fixture.client) {
            fixture.start();
            when(fixture.scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                    .thenThrow(new RejectedExecutionException("expected scheduling failure"));
            assertThrows(RejectedExecutionException.class, () -> client.executeSubscription(() -> {}));
            when(fixture.scheduler.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                    .thenAnswer(_ -> {
                        client.stopConsumer();
                        throw new RejectedExecutionException("shutdown race");
                    });
            client.executeSubscription(() -> {});
            assertFalse(client.isReady());
        }
    }

    @Test
    public void independentNodesKeepDifferentGroupsAndIndependentLifecycles() throws Exception {
        var first = new Fixture("node-1");
        var second = new Fixture("node-2");
        try (var firstClient = first.client; var secondClient = second.client) {
            first.start();
            second.start();
            verify(first.consumerBuilder).setConsumerGroup("test-group-node-1");
            verify(second.consumerBuilder).setConsumerGroup("test-group-node-2");
            firstClient.stopConsumer();
            assertTrue(secondClient.isReady());
            verify(second.consumer, never()).close();
        }
    }

    @Test
    public void configurationBindsOriginalPrefixWithoutDuplicateBeansOrChatDependencies() {
        assertTrue(Arrays.asList(TransactionAutoConfiguration.class.getAnnotation(Import.class).value())
                .contains(LiteTopicPushConfiguration.class));
        ScheduledExecutorService scheduler;
        LiteTopicClient client;
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "openchat.chat.push.lite.enabled", "false",
                    "openchat.chat.push.lite.node-id", "test-node",
                    "openchat.chat.push.lite.parent-topic", "test-parent")));
            context.register(LiteTopicPushConfiguration.class, ImportConfiguration.class);
            context.refresh();
            assertEquals(1, context.getBeansOfType(LiteTopicPushProperties.class).size());
            assertEquals(1, context.getBeansOfType(LiteTopicClient.class).size());
            assertEquals(1, context.getBeansOfType(MqConsumerShutdown.class).size());
            var properties = context.getBean(LiteTopicPushProperties.class);
            assertEquals("test-node", properties.getNodeId());
            assertEquals("test-parent", properties.getParentTopic());
            client = context.getBean(LiteTopicClient.class);
            assertFalse(client.isReady());
            scheduler = context.getBean(LiteTopicPushConfiguration.SUBSCRIPTION_EXECUTOR,
                    ScheduledExecutorService.class);
        }
        assertTrue(scheduler.isShutdown());
        assertFalse(client.isReady());
    }

    @Configuration(proxyBeanMethods = false)
    @Import(LiteTopicPushConfiguration.class)
    static class ImportConfiguration {
    }

    private static final class Fixture {
        private final LiteTopicPushProperties properties = new LiteTopicPushProperties();
        private final ClientServiceProvider provider = mock(ClientServiceProvider.class);
        private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        private final ProducerBuilder producerBuilder = mock(ProducerBuilder.class, RETURNS_SELF);
        private final LitePushConsumerBuilder consumerBuilder = mock(LitePushConsumerBuilder.class, RETURNS_SELF);
        private final Producer producer = mock(Producer.class);
        private final LitePushConsumer consumer = mock(LitePushConsumer.class);
        private final LiteTopicClient client = new LiteTopicClient(properties, provider, scheduler);

        private Fixture(String nodeId) throws ClientException {
            properties.setEnabled(true);
            properties.setEndpoints("127.0.0.1:1");
            properties.setNodeId(nodeId);
            properties.setConsumerGroup("test-group");
            properties.setParentTopic("test-parent");
            when(provider.newProducerBuilder()).thenReturn(producerBuilder);
            when(provider.newLitePushConsumerBuilder()).thenReturn(consumerBuilder);
            when(producerBuilder.build()).thenReturn(producer);
            when(consumerBuilder.build()).thenReturn(consumer);
        }

        private void start() {
            client.start(_ -> ConsumeResult.SUCCESS, () -> {});
        }
    }
}
