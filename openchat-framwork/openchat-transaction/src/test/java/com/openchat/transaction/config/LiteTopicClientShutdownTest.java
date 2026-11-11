package com.openchat.transaction.config;

import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.LitePushConsumer;
import org.apache.rocketmq.client.apis.consumer.LitePushConsumerBuilder;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.ProducerBuilder;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LiteTopicClientShutdownTest {

    @Test(timeout = 15000)
    public void closeWaitsForStartupAndPreventsRestart() throws Exception {
        var properties = new LiteTopicPushProperties();
        properties.setEnabled(true);
        properties.setEndpoints("127.0.0.1:1");
        properties.setNodeId("shutdown-test");
        var scheduler = mock(ScheduledExecutorService.class);
        LiteTopicClient lite = createLite(properties, scheduler);
        var provider = mock(ClientServiceProvider.class);
        var producerBuilder = mock(ProducerBuilder.class, RETURNS_SELF);
        var consumerBuilder = mock(LitePushConsumerBuilder.class, RETURNS_SELF);
        var producer = mock(Producer.class);
        var consumer = mock(LitePushConsumer.class);
        when(provider.newProducerBuilder()).thenReturn(producerBuilder);
        when(provider.newLitePushConsumerBuilder()).thenReturn(consumerBuilder);
        when(producerBuilder.build()).thenReturn(producer);
        var building = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(consumerBuilder.build()).thenAnswer(_ -> {
            building.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return consumer;
        });
        ReflectionTestUtils.setField(lite, "provider", provider);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var starting = executor.submit(() -> start(lite));
            try {
                assertTrue(building.await(5, TimeUnit.SECONDS));
                var closeEntered = new CountDownLatch(1);
                var closing = executor.submit(() -> {
                    closeEntered.countDown();
                    lite.close();
                });
                assertTrue(closeEntered.await(5, TimeUnit.SECONDS));
                assertFalse(closing.isDone());
                release.countDown();
                starting.get(5, TimeUnit.SECONDS);
                closing.get(5, TimeUnit.SECONDS);
                start(lite);
                lite.close();
                assertFalse(lite.isReady());
                verify(consumerBuilder).build();
                verify(consumer).close();
                verify(producer).close();
                verify(scheduler).shutdownNow();
            } finally {
                release.countDown();
            }
        } finally {
            lite.close();
        }
    }

    @Test(timeout = 15000)
    public void duplicateCloseWaitsForConsumerAndClosesProducerOnlyAfterDrain() throws Exception {
        var scheduler = mock(ScheduledExecutorService.class);
        LiteTopicClient lite = createLite(new LiteTopicPushProperties(), scheduler);
        var consumer = mock(LitePushConsumer.class);
        var producer = mock(Producer.class);
        ReflectionTestUtils.setField(lite, "consumer", consumer);
        ReflectionTestUtils.setField(lite, "producer", producer);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(_ -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        }).when(consumer).close();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(lite::close);
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var secondEntered = new CountDownLatch(1);
                var second = executor.submit(() -> {
                    secondEntered.countDown();
                    lite.close();
                });
                assertTrue(secondEntered.await(5, TimeUnit.SECONDS));
                assertFalse(second.isDone());
                verify(producer, never()).close();
                verify(scheduler, never()).shutdownNow();
                release.countDown();
                first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
                verify(consumer).close();
                verify(producer).close();
                verify(scheduler).shutdownNow();
            } finally {
                release.countDown();
            }
        } finally {
            lite.close();
        }
    }

    @Test
    public void consumerFailureStillClosesProducerAndAllowsDestructionRetry() throws Exception {
        var scheduler = mock(ScheduledExecutorService.class);
        LiteTopicClient lite = createLite(new LiteTopicPushProperties(), scheduler);
        var consumer = mock(LitePushConsumer.class);
        var producer = mock(Producer.class);
        ReflectionTestUtils.setField(lite, "consumer", consumer);
        ReflectionTestUtils.setField(lite, "producer", producer);
        doThrow(new IllegalStateException("expected close failure")).when(consumer).close();
        assertThrows(IllegalStateException.class, lite::close);
        verify(producer).close();
        verify(scheduler).shutdownNow();
        assertFalse(lite.isReady());
        assertSame(consumer, ReflectionTestUtils.getField(lite, "consumer"));
        assertNull(ReflectionTestUtils.getField(lite, "producer"));
        doNothing().when(consumer).close();
        lite.close();
        verify(consumer, times(2)).close();
        verify(producer).close();
        verify(scheduler).shutdownNow();
        assertNull(ReflectionTestUtils.getField(lite, "consumer"));
    }

    @Test
    public void earlyConsumerStopKeepsProducerUntilBeanDestruction() throws Exception {
        var scheduler = mock(ScheduledExecutorService.class);
        LiteTopicClient lite = createLite(new LiteTopicPushProperties(), scheduler);
        var consumer = mock(LitePushConsumer.class);
        var producer = mock(Producer.class);
        ReflectionTestUtils.setField(lite, "consumer", consumer);
        ReflectionTestUtils.setField(lite, "producer", producer);
        lite.stopConsumer();
        lite.stopConsumer();
        assertFalse(lite.isReady());
        assertFalse(lite.send("test-topic", "test-key", new byte[0]));
        verify(consumer).close();
        verify(producer, never()).close();
        verify(scheduler).shutdownNow();
        lite.close();
        lite.close();
        verify(consumer).close();
        verify(producer).close();
    }

    @Test
    public void checkedCloseFailureIsReportedAndRetriedDuringDestruction() throws Exception {
        var scheduler = mock(ScheduledExecutorService.class);
        LiteTopicClient lite = createLite(new LiteTopicPushProperties(), scheduler);
        var consumer = mock(LitePushConsumer.class);
        var producer = mock(Producer.class);
        ReflectionTestUtils.setField(lite, "consumer", consumer);
        ReflectionTestUtils.setField(lite, "producer", producer);
        doThrow(new java.io.IOException("expected close failure")).when(consumer).close();
        assertThrows(IllegalStateException.class, lite::stopConsumer);
        verify(producer, never()).close();
        doNothing().when(consumer).close();
        lite.close();
        verify(consumer, times(2)).close();
        verify(producer).close();
    }

    @Test
    public void disabledAndAlreadyClosedInstanceDoesNotBuildClients() {
        var scheduler = mock(ScheduledExecutorService.class);
        var properties = new LiteTopicPushProperties();
        LiteTopicClient lite = createLite(properties, scheduler);
        var provider = mock(ClientServiceProvider.class);
        ReflectionTestUtils.setField(lite, "provider", provider);
        start(lite);
        lite.close();
        properties.setEnabled(true);
        start(lite);
        lite.close();
        verifyNoInteractions(provider);
        verify(scheduler).shutdownNow();
        assertFalse(lite.isReady());
    }

    private static LiteTopicClient createLite(LiteTopicPushProperties properties,
                                            ScheduledExecutorService scheduler) {
        return new LiteTopicClient(properties, mock(ClientServiceProvider.class), scheduler);
    }

    private static void start(LiteTopicClient client) {
        client.start(_ -> ConsumeResult.SUCCESS, () -> {});
    }
}
