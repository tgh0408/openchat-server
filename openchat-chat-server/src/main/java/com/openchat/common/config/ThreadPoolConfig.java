package com.openchat.common.config;

import com.openchat.common.factory.MyThreadFactory;
import com.openchat.common.sse.config.SseProperties;
import com.openchat.transaction.annotation.SecureInvokeConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableAsync
public class ThreadPoolConfig implements AsyncConfigurer, SecureInvokeConfigurer {
    /**
     * 项目共用线程池
     */
    public static final String OPENCHAT_EXECUTOR = "openchatExecutor";

    /** 普通定时任务的默认调度器，与心跳及订阅重试隔离。 */
    public static final String TASK_SCHEDULER = "taskScheduler";

    /** IP 归属地查询专用线程池，隔离第三方网络和重试等待。 */
    public static final String IP_DETAIL_EXECUTOR = "ipDetailExecutor";

    /** SSE 推送专用线程池，避免慢连接占用业务线程。 */
    public static final String SSE_PUSH_EXECUTOR = "ssePushExecutor";

    /** SSE 心跳专用调度池，不与订阅重试竞争线程。 */
    public static final String SSE_HEARTBEAT_EXECUTOR = "sseHeartbeatExecutor";

    @Override
    public Executor getAsyncExecutor() {
        return openchatExecutor();
    }

    @Override
    public Executor getSecureInvokeExecutor(){return openchatExecutor();}

    @Bean(OPENCHAT_EXECUTOR)
    @Primary
    public ThreadPoolTaskExecutor openchatExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setCorePoolSize(72);
        executor.setMaxPoolSize(288);
        executor.setQueueCapacity(10000);
        //线程前缀，给线程前面加名字方便区分
        executor.setThreadNamePrefix("openchat-executor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());//满了谁放的谁执行执行，认为重要任务
        executor.setThreadFactory(new MyThreadFactory(executor));
        executor.initialize();
        return executor;
    }

    /** 三类业务定时任务可并行执行，同一 fixedDelay 任务仍在完成后再计时。 */
    @Bean(TASK_SCHEDULER)
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("openchat-scheduler-");
        scheduler.setRemoveOnCancelPolicy(true);
        // 停机不再执行排队的延迟或周期任务，未完成业务由持久化状态恢复。
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }

    @Bean(IP_DETAIL_EXECUTOR)
    public ThreadPoolTaskExecutor ipDetailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("ip-detail-executor-");
        // 满载时交由业务记录失败，不让注册请求线程承担网络查询。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setThreadFactory(new MyThreadFactory(executor));
        // 尽力补全任务不阻碍停机：取消排队任务，中断执行线程并有界等待。
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Bean(name = SSE_PUSH_EXECUTOR, destroyMethod = "shutdownNow")
    @ConditionalOnProperty(value = "sse.enabled", havingValue = "true")
    public ThreadPoolExecutor ssePushExecutor(SseProperties properties) {
        int coreThreads = properties.getPushCoreThreads();
        int maxThreads = properties.getPushMaxThreads();
        int queueCapacity = properties.getPushQueueCapacity();
        if (coreThreads <= 0 || maxThreads < coreThreads || queueCapacity <= 0) {
            throw new IllegalArgumentException("Invalid SSE push executor configuration");
        }
        // 满载或停机时显式拒绝，使推送 Future 能感知失败，也避免调用线程承担慢连接写入。
        return new ThreadPoolExecutor(coreThreads, maxThreads, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity), daemonThreadFactory("sse-push-"),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(name = SSE_HEARTBEAT_EXECUTOR, destroyMethod = "shutdownNow")
    @ConditionalOnProperty(value = "sse.enabled", havingValue = "true")
    public ScheduledExecutorService sseHeartbeatExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sse-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 为独立的守护线程池分配名称，不跨池共享序号。 */
    private ThreadFactory daemonThreadFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

}
