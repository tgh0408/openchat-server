package com.openchat.transaction.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.support.DefaultListenerContainer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 在 Spring 串行停止生命周期组件之前，并行等待本节点 MQ 消费者正常关闭。
 * 不改变消费确认、重试或跨节点投递语义，也不提前销毁消费依赖的数据源和生产者。
 */
@Slf4j
@RequiredArgsConstructor
public class MqConsumerShutdown implements ApplicationListener<ContextClosedEvent> {

    private final ConfigurableApplicationContext applicationContext;
    private boolean stopped;

    @Override
    public boolean supportsAsyncExecution() {
        // 即使事件广播器配置了线程池，也必须在生命周期停机和 Bean 销毁前等待完成。
        return false;
    }

    @Override
    public synchronized void onApplicationEvent(ContextClosedEvent event) {
        if (event.getApplicationContext() != applicationContext || stopped) {
            return;
        }
        stopped = true;
        List<ShutdownTarget> targets = new ArrayList<>();
        var beanFactory = applicationContext.getBeanFactory();
        // 只处理本上下文已经创建的实例；启动失败或关闭时不能再实例化懒加载消费者。
        for (String name : beanFactory.getSingletonNames()) {
            Object bean = beanFactory.getSingleton(name);
            if (bean instanceof DefaultListenerContainer container) {
                targets.add(new ShutdownTarget(name, container::stop));
            } else if (bean instanceof LiteTopicClient liteTopicClient) {
                targets.add(new ShutdownTarget(name, liteTopicClient::stopConsumer));
            }
        }
        if (targets.isEmpty()) {
            return;
        }

        long startedAt = System.nanoTime();
        log.info("开始并行关闭本机 MQ 消费者, count={}", targets.size());
        var executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("mq-shutdown-", 0).factory());
        List<CompletableFuture<Void>> completions = new ArrayList<>();
        try {
            for (ShutdownTarget target : targets) {
                completions.add(CompletableFuture.runAsync(() -> closeTarget(target), executor));
            }
            // join 不会因停机线程被中断而取消 SDK 的正常关闭或 ACK 等待。
            CompletableFuture.allOf(completions.toArray(CompletableFuture[]::new)).join();
            log.info("本机 MQ 消费者并行关闭完成, count={}, elapsedMs={}",
                    targets.size(), elapsedMillis(startedAt));
        } catch (CompletionException exception) {
            // allOf 已等到所有任务结束；失败实例仍由后续官方生命周期和销毁方法兜底。
            log.error("本机 MQ 消费者并行关闭存在失败, elapsedMs={}",
                    elapsedMillis(startedAt), exception.getCause());
        } finally {
            executor.shutdown();
        }
    }

    private void closeTarget(ShutdownTarget target) {
        long startedAt = System.nanoTime();
        log.info("开始正常关闭 MQ 消费者, bean={}", target.name());
        try {
            target.close().run();
            log.info("MQ 消费者正常关闭完成, bean={}, elapsedMs={}",
                    target.name(), elapsedMillis(startedAt));
        } catch (RuntimeException exception) {
            log.error("MQ 消费者正常关闭失败, bean={}, elapsedMs={}",
                    target.name(), elapsedMillis(startedAt), exception);
            throw exception;
        }
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private record ShutdownTarget(String name, Runnable close) {
    }
}
