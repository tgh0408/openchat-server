package com.openchat.transaction.config;

import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.support.DefaultListenerContainer;
import org.junit.Test;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MqConsumerShutdownTest {

    @Test(timeout = 15000)
    public void closesAllConsumersInParallelBeforeLifecycleAndBeanDestruction() throws Exception {
        var context = new GenericApplicationContext();
        var entered = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var completed = new AtomicInteger();
        var lifecycleStopped = new AtomicBoolean();
        var destroyed = new AtomicBoolean();
        var prematureCleanup = new AtomicBoolean();
        List<PushConsumer> consumers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            PushConsumer consumer = mock(PushConsumer.class);
            doAnswer(_ -> {
                awaitRelease(entered, release);
                completed.incrementAndGet();
                return null;
            }).when(consumer).close();
            var container = new DefaultListenerContainer();
            ReflectionTestUtils.setField(container, "pushConsumer", consumer);
            container.setRunning(true);
            context.getBeanFactory().registerSingleton("consumer" + i, container);
            consumers.add(consumer);
        }
        LiteTopicClient lite = mock(LiteTopicClient.class);
        doAnswer(_ -> {
            awaitRelease(entered, release);
            completed.incrementAndGet();
            return null;
        }).when(lite).stopConsumer();
        context.getBeanFactory().registerSingleton("liteTopicClient", lite);
        context.getBeanFactory().registerSingleton("lifecycle", new SmartLifecycle() {
            private boolean running = true;

            @Override
            public void start() {
                running = true;
            }

            @Override
            public void stop() {
                prematureCleanup.set(completed.get() != 4);
                lifecycleStopped.set(true);
                running = false;
            }

            @Override
            public boolean isRunning() {
                return running;
            }
        });
        context.getDefaultListableBeanFactory().registerDisposableBean("resource", () -> {
            if (completed.get() != 4) {
                prematureCleanup.set(true);
            }
            destroyed.set(true);
        });
        context.registerBean(MqConsumerShutdown.class, () -> new MqConsumerShutdown(context));
        context.refresh();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var closing = executor.submit(context::close);
            try {
                assertTrue("All close calls must enter before any is released",
                        entered.await(5, TimeUnit.SECONDS));
                assertFalse(closing.isDone());
                assertFalse(lifecycleStopped.get());
                assertFalse(destroyed.get());
            } finally {
                release.countDown();
            }
            closing.get(5, TimeUnit.SECONDS);
            assertTrue(lifecycleStopped.get());
            assertTrue(destroyed.get());
            assertFalse(prematureCleanup.get());
            for (PushConsumer consumer : consumers) {
                verify(consumer).close();
            }
            verify(lite).stopConsumer();
            verify(lite, never()).close();
        } finally {
            release.countDown();
            context.close();
        }
    }

    @Test(timeout = 15000)
    public void oneFailureDoesNotSkipOrStopWaitingForOtherConsumers() throws Exception {
        try (var context = new GenericApplicationContext();
             var executor = Executors.newSingleThreadExecutor()) {
            var failed = mock(DefaultListenerContainer.class);
            var slow = mock(DefaultListenerContainer.class);
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            doThrow(new IllegalStateException("expected close failure")).when(failed).stop();
            doAnswer(_ -> {
                awaitRelease(entered, release);
                return null;
            }).when(slow).stop();
            context.getBeanFactory().registerSingleton("failed", failed);
            context.getBeanFactory().registerSingleton("slow", slow);
            context.refresh();
            var coordinator = new MqConsumerShutdown(context);
            var closing = executor.submit(() ->
                    coordinator.onApplicationEvent(new ContextClosedEvent(context)));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertFalse(closing.isDone());
            } finally {
                release.countDown();
            }
            closing.get(5, TimeUnit.SECONDS);
            verify(failed).stop();
            verify(slow).stop();
        }
    }

    @Test(timeout = 15000)
    public void repeatedCloseEventsWaitForSameRoundWithoutDuplicateClose() throws Exception {
        try (var context = new GenericApplicationContext();
             var executor = Executors.newFixedThreadPool(2)) {
            var consumer = mock(DefaultListenerContainer.class);
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            doAnswer(_ -> {
                awaitRelease(entered, release);
                return null;
            }).when(consumer).stop();
            context.getBeanFactory().registerSingleton("consumer", consumer);
            context.refresh();
            var coordinator = new MqConsumerShutdown(context);
            var first = executor.submit(() ->
                    coordinator.onApplicationEvent(new ContextClosedEvent(context)));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var secondEntered = new CountDownLatch(1);
                var second = executor.submit(() -> {
                    secondEntered.countDown();
                    coordinator.onApplicationEvent(new ContextClosedEvent(context));
                });
                assertTrue(secondEntered.await(5, TimeUnit.SECONDS));
                assertFalse(second.isDone());
                release.countDown();
                first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
                verify(consumer).stop();
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    public void ignoresForeignContextAndDoesNotCreateLazyConsumers() {
        try (var context = new GenericApplicationContext();
             var child = new GenericApplicationContext(context)) {
            var consumer = mock(DefaultListenerContainer.class);
            var created = new AtomicBoolean();
            context.getBeanFactory().registerSingleton("consumer", consumer);
            context.refresh();
            context.registerBean("lazyConsumer", DefaultListenerContainer.class, () -> {
                created.set(true);
                return mock(DefaultListenerContainer.class);
            }, definition -> definition.setLazyInit(true));
            var coordinator = new MqConsumerShutdown(context);
            assertFalse(coordinator.supportsAsyncExecution());
            coordinator.onApplicationEvent(new ContextClosedEvent(child));
            verify(consumer, never()).stop();
            coordinator.onApplicationEvent(new ContextClosedEvent(context));
            verify(consumer).stop();
            assertFalse(created.get());
        }
    }

    @Test(timeout = 15000)
    public void interruptingShutdownThreadDoesNotInterruptConsumerDrain() throws Exception {
        try (var context = new GenericApplicationContext()) {
            var consumer = mock(DefaultListenerContainer.class);
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var interrupted = new AtomicBoolean();
            doAnswer(_ -> {
                try {
                    awaitRelease(entered, release);
                } catch (InterruptedException exception) {
                    interrupted.set(true);
                    throw exception;
                }
                return null;
            }).when(consumer).stop();
            context.getBeanFactory().registerSingleton("consumer", consumer);
            context.refresh();
            var coordinator = new MqConsumerShutdown(context);
            var finished = new CountDownLatch(1);
            var closing = Thread.ofPlatform().start(() -> {
                try {
                    coordinator.onApplicationEvent(new ContextClosedEvent(context));
                } finally {
                    finished.countDown();
                }
            });
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                closing.interrupt();
                assertFalse(finished.await(100, TimeUnit.MILLISECONDS));
                assertFalse(interrupted.get());
            } finally {
                release.countDown();
                closing.join(5000);
            }
            assertFalse(closing.isAlive());
            assertFalse(interrupted.get());
        }
    }

    @Test
    public void emptyContextCanCloseRepeatedly() {
        try (var context = new GenericApplicationContext()) {
            context.refresh();
            var coordinator = new MqConsumerShutdown(context);
            coordinator.onApplicationEvent(new ContextClosedEvent(context));
            coordinator.onApplicationEvent(new ContextClosedEvent(context));
        }
    }

    private static void awaitRelease(CountDownLatch entered, CountDownLatch release)
            throws InterruptedException {
        entered.countDown();
        assertTrue("Timed out waiting for test release", release.await(5, TimeUnit.SECONDS));
    }
}
