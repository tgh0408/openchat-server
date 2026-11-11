package com.openchat.common.sse.core;

import com.openchat.common.event.SseConnectionChangedEvent;
import com.openchat.common.event.LoginSessionChangedEvent;
import com.openchat.common.constant.MQConstant;
import com.openchat.rtc.domain.RtcAuditEvent;
import com.openchat.common.sse.config.SseProperties;
import com.openchat.common.sse.dto.SseMessageDto;
import com.openchat.transaction.service.MQProducer;
import com.openchat.user.service.LoginSessionStore;
import com.openchat.user.domain.enums.TerminalEnum;
import com.openchat.common.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 管理 Server-Sent Events (SSE) 连接
 *
 * @author Lion Li
 */
@Slf4j
public class SseEmitterManager implements SmartLifecycle {

    private static final long TIMEOUT = 60;

    private final Map<Long, UserEmitters> USER_TOKEN_EMITTERS = new ConcurrentHashMap<>();

    private final MQProducer mqProducer;
    private final LoginSessionStore sessions;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ThreadPoolExecutor pushExecutor;
    private final ScheduledExecutorService heartbeatExecutor;

    /** 平台来自已完成 JWT 校验的请求上下文，不从未验签的 token 推断。 */
    private record SessionKey(TerminalEnum terminal, String token) {
    }

    /**
     * 同一用户的连接操作必须串行，避免旧 token 连接尚未关闭就被新连接覆盖。
     */
    private static final class UserEmitters {
        private final Map<SessionKey, SseEmitter> values = new HashMap<>();
        private boolean replacing;
    }

    public SseEmitterManager(MQProducer mqProducer, SseProperties properties,
            ApplicationEventPublisher applicationEventPublisher,
            ThreadPoolExecutor pushExecutor, ScheduledExecutorService heartbeatExecutor,
            LoginSessionStore sessions) {
        this.mqProducer = mqProducer;
        this.sessions = sessions;
        this.applicationEventPublisher = applicationEventPublisher;
        int heartbeatIntervalSeconds = properties.getHeartbeatIntervalSeconds();
        if (heartbeatIntervalSeconds <= 0) {
            throw new IllegalArgumentException("Invalid SSE heartbeat interval");
        }
        this.pushExecutor = pushExecutor;
        this.heartbeatExecutor = heartbeatExecutor;
    }

    /**
     * 建立与指定用户的 SSE 连接
     *
     * @param userId 用户的唯一标识符，用于区分不同用户的连接
     * @param token  用户的唯一令牌，用于识别具体的连接
     * @param terminal 已验证的登录平台
     * @return 返回一个 SseEmitter 实例，客户端可以通过该实例接收 SSE 事件
     */
    public SseEmitter connect(Long userId, String token, TerminalEnum terminal) {
        Objects.requireNonNull(terminal, "Missing login terminal");
        SessionKey session = new SessionKey(terminal, token);
        if (!running.get()) {
            throw new IllegalStateException("SSE service is shutting down");
        }
        SseEmitter emitter = new SseEmitter(86400000L);
        // 从 USER_TOKEN_EMITTERS 中获取或创建当前用户的连接表
        // 每个用户可以有多个 SSE 连接，通过 token 进行区分
        // 当 emitter 完成、超时或发生错误时，从映射表中移除对应的 token
        emitter.onCompletion(() -> {
            log.info("SSE 连接完成, uid={}", userId);
            removeEmitter(userId, session, emitter);
        });
        emitter.onTimeout(() -> {
            log.info("SSE 连接超时, uid={}", userId);
            removeEmitter(userId, session, emitter);
            // 标记为完成，阻止 Spring 将超时再次分派给全局异常处理器。
            emitter.complete();
        });
        emitter.onError((e) -> {
            log.info(
                    "SSE 连接断开, uid={}, exceptionType={}, message={}",
                    userId,
                    e.getClass().getName(),
                    e.getMessage());
            removeEmitter(userId, session, emitter);
        });

        boolean wasConnected = USER_TOKEN_EMITTERS.containsKey(userId);
        while (true) {
            UserEmitters userEmitters = USER_TOKEN_EMITTERS.computeIfAbsent(
                userId, k -> new UserEmitters());
            synchronized (userEmitters) {
                // 空表可能刚被旧连接回调摘除，不能向脱离主映射的表注册新连接。
                if (USER_TOKEN_EMITTERS.get(userId) != userEmitters) continue;
                // 停机可能发生在注册前后；二次检查确保迟到连接不会遗留在连接表中。
                if (!running.get()) {
                    removeUserEmittersIfEmpty(userId, userEmitters);
                    closeEmitter(emitter, false);
                    throw new IllegalStateException("SSE service is shutting down");
                }
                String currentToken;
                try {
                    currentToken = sessions.currentToken(userId, terminal);
                } catch (RuntimeException exception) {
                    removeUserEmittersIfEmpty(userId, userEmitters);
                    closeEmitter(emitter, false);
                    throw exception;
                }
                if (token == null || !token.equals(currentToken)) {
                    removeUserEmittersIfEmpty(userId, userEmitters);
                    revokeEmitter(userId, session, emitter, currentToken);
                    return emitter;
                }

                userEmitters.replacing = true;
                try {
                    SseEmitter previous = userEmitters.values.get(session);
                    if (previous != null) {
                        // 旧连接仍保留在表中，直到 complete() 返回，确保新连接不会先注册。
                        closeEmitter(previous, false);
                        userEmitters.values.remove(session, previous);
                    }
                    userEmitters.values.put(session, emitter);
                } finally {
                    userEmitters.replacing = false;
                    removeUserEmittersIfEmpty(userId, userEmitters);
                }
                break;
            }
        }

        try {
            // 覆盖拦截器校验或本机注册期间发生的新登录，以及先于注册到达的广播。
            reconcileSessions(userId);
        } catch (RuntimeException exception) {
            removeEmitter(userId, session, emitter);
            closeEmitter(emitter, false);
            throw exception;
        }
        if (!containsEmitter(userId, session, emitter)) return emitter;
        try {
            // 向客户端发送一条连接成功的事件
            emitter.send(SseEmitter.event().comment("connected"));
            log.info("SSE 连接建立, uid={}", userId);
            if (!wasConnected) {
                applicationEventPublisher.publishEvent(new SseConnectionChangedEvent(userId, true));
            }
        } catch (Exception e) {
            // 首次写入失败说明客户端可能已经断开，不能再次 complete 已失效的响应。
            log.info(
                    "SSE 首次写入失败，连接已视为断开, uid={}, exceptionType={}, message={}",
                    userId,
                    e.getClass().getName(),
                    e.getMessage());
            removeEmitter(userId, session, emitter);
        }
        return emitter;
    }

    /**
     * 断开指定用户的 SSE 连接
     *
     * @param userId 用户的唯一标识符，用于区分不同用户的连接
     * @param token  用户的唯一令牌，用于识别具体的连接
     */
    public void disconnect(Long userId, String token, TerminalEnum terminal) {
        if (userId == null || token == null || terminal == null) {
            return;
        }
        UserEmitters userEmitters = USER_TOKEN_EMITTERS.get(userId);
        if (userEmitters != null) {
            boolean disconnected;
            synchronized (userEmitters) {
                SseEmitter sseEmitter = userEmitters.values.remove(new SessionKey(terminal, token));
                if (sseEmitter == null) {
                    log.info("SSE 显式关闭未找到本机连接, uid={}", userId);
                    removeUserEmittersIfEmpty(userId, userEmitters);
                    return;
                }
                closeEmitter(sseEmitter, true);
                log.info("SSE 本机连接已显式关闭, uid={}", userId);
                disconnected = userEmitters.values.isEmpty();
                removeUserEmittersIfEmpty(userId, userEmitters);
            }
            if (disconnected) {
                applicationEventPublisher.publishEvent(new SseConnectionChangedEvent(userId, false));
            }
        }
    }


    private void disconnectAll() {
        // 先摘除当前快照，避免关闭过程中建立的新连接被误清理。
        Map<Long, UserEmitters> emittersSnapshot = new HashMap<>(USER_TOKEN_EMITTERS);
        USER_TOKEN_EMITTERS.clear();
        emittersSnapshot.forEach((_, userEmitters) -> {
            synchronized (userEmitters) {
                userEmitters.replacing = true;
                try {
                    Map<SessionKey, SseEmitter> valuesSnapshot = new HashMap<>(userEmitters.values);
                    valuesSnapshot.values().forEach(emitter -> {
                        closeEmitter(emitter, false);
                    });
                    userEmitters.values.clear();
                } finally {
                    userEmitters.replacing = false;
                }
            }
        });
    }

    /**
     * 向指定的用户会话发送消息
     *
     * @param userId  要发送消息的用户id
     * @param message 要发送的消息内容
     */
    public void sendMessage(Long userId, String message) {
        sendEvent(userId, "message", null, message);
    }

    /**
     * 向指定用户的本机 SSE 连接发送带事件名和事件 ID 的消息。
     */
    public void sendEvent(Long userId, String eventName, String eventId, String message) {
        Map<SessionKey, SseEmitter> snapshot = validatedSnapshot(userId);
        for (Map.Entry<SessionKey, SseEmitter> entry : snapshot.entrySet()) {
            if (!containsEmitter(userId, entry.getKey(), entry.getValue())) continue;
            try {
                SseEmitter.SseEventBuilder event = SseEmitter.event().name(eventName).data(message);
                if (eventId != null && !eventId.isBlank()) {
                    event.id(eventId);
                }
                entry.getValue().send(event);
                if ("rtc".equals(eventName))
                    log.info("event={} eventId={} uid={} terminal={}",
                            RtcAuditEvent.RTC_SSE_WRITE_COMPLETED, eventId, userId, entry.getKey().terminal());
            } catch (Exception e) {
                if ("rtc".equals(eventName))
                    log.warn("event={} eventId={} uid={} terminal={} exceptionType={}",
                            RtcAuditEvent.RTC_SSE_WRITE_FAILED, eventId, userId, entry.getKey().terminal(), e.getClass().getSimpleName());
                // 写入失败按离线处理，不重试整批推送；只移除连接，不再调用 complete。
                log.info(
                        "SSE 推送写入失败，移除连接, uid={}, exceptionType={}, message={}",
                        userId,
                        e.getClass().getName(),
                        e.getMessage());
                removeEmitter(userId, entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 本机全用户会话发送消息
     *
     */
    public Set<Long> getConnectedUserIds() {
        return Set.copyOf(USER_TOKEN_EMITTERS.keySet());
    }

    /**
     * 汇总本节点各用户的 SSE 写入任务；写入失败按离线处理，任务提交、校验失败或等待超时仍异常完成。
     * 无本机有效连接时不发送，不代表客户端已接收，也不等待其他节点。
     */
    public CompletableFuture<Void> sendMessageToUsersAsync(Collection<Long> userIds, String message) {
        if (userIds == null || userIds.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Collection<Long> recipients = new ArrayList<>(userIds);
        Collection<CompletableFuture<Void>> tasks = new ArrayList<>(recipients.size());
        for (Long userId : recipients) {
            CompletableFuture<Void> task;
            try {
                task = CompletableFuture.runAsync(
                        () -> sendEvent(userId, "message", null, message), pushExecutor);
            } catch (RejectedExecutionException exception) {
                task = CompletableFuture.failedFuture(exception);
            }
            tasks.add(task);
        }
        return CompletableFuture.allOf(tasks.toArray(CompletableFuture<?>[]::new))
                .whenComplete((_, exception) -> {
                    if (exception != null) {
                        log.warn("SSE batch push failed for {} users", recipients.size(), exception);
                    }
                })
                .orTimeout(TIMEOUT, TimeUnit.SECONDS);
    }

    public void sendMessage(String message) {
        for (Long userId : USER_TOKEN_EMITTERS.keySet()) {
            sendMessage(userId, message);
        }
    }

    /**
     * 发送 SSE 注释心跳，避免中间代理或客户端把无业务数据的长连接判定为超时。
     */
    public void sendHeartbeats() {
        if (!running.get()) {
            return;
        }
        for (Long userId : USER_TOKEN_EMITTERS.keySet()) {
            Map<SessionKey, SseEmitter> snapshot;
            try {
                snapshot = validatedSnapshot(userId);
            } catch (RuntimeException exception) {
                // Redis 故障不等于令牌失效，不发送业务数据，也不让周期调度永久停止。
                log.warn("SSE session verification unavailable, uid={}", userId);
                continue;
            }
            for (Map.Entry<SessionKey, SseEmitter> entry : snapshot.entrySet()) {
                if (!containsEmitter(userId, entry.getKey(), entry.getValue())) continue;
                try {
                    entry.getValue().send(SseEmitter.event().comment("heartbeat"));
                } catch (Exception exception) {
                    // 心跳是检测断开的路径；写入失败后不能再次 complete 失效响应。
                    log.info(
                            "SSE 心跳写入失败，移除连接, uid={}, exceptionType={}, message={}",
                            userId,
                            exception.getClass().getName(),
                            exception.getMessage());
                    removeEmitter(userId, entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /** 登录或退出只提交本机通知，避免慢连接阻塞请求线程；队列满时由心跳兜底。 */
    @EventListener
    public void onLoginSessionChanged(LoginSessionChangedEvent event) {
        try {
            pushExecutor.execute(() -> {
                try {
                    reconcileSessions(event.uid());
                } catch (RuntimeException exception) {
                    log.warn("Local SSE session reconciliation failed, uid={}", event.uid());
                }
            });
        } catch (RejectedExecutionException exception) {
            log.warn("SSE session notification queue is full, uid={}", event.uid());
        }
    }

    /** 仅撤销 Redis 已不再承认的连接，重复和乱序广播均不影响当前令牌。 */
    public void reconcileSessions(Long userId) {
        validatedSnapshot(userId);
    }

    private Map<SessionKey, SseEmitter> validatedSnapshot(Long userId) {
        UserEmitters userEmitters = USER_TOKEN_EMITTERS.get(userId);
        if (userEmitters == null) return Map.of();
        synchronized (userEmitters) {
            Map<SessionKey, SseEmitter> snapshot = new HashMap<>(userEmitters.values);
            Map<TerminalEnum, String> currentTokens = new EnumMap<>(TerminalEnum.class);
            // 每个有连接的平台只读一次；全部读取成功后才处理，Redis 异常不冒充过期。
            for (SessionKey session : snapshot.keySet()) {
                if (!currentTokens.containsKey(session.terminal())) {
                    currentTokens.put(session.terminal(), sessions.currentToken(userId, session.terminal()));
                }
            }
            for (Map.Entry<SessionKey, SseEmitter> entry : snapshot.entrySet()) {
                String currentToken = currentTokens.get(entry.getKey().terminal());
                if (!Objects.equals(entry.getKey().token(), currentToken)) {
                    revokeEmitter(userId, entry.getKey(), entry.getValue(), currentToken);
                }
            }
            return new HashMap<>(userEmitters.values);
        }
    }

    private boolean containsEmitter(Long userId, SessionKey session, SseEmitter emitter) {
        UserEmitters userEmitters = USER_TOKEN_EMITTERS.get(userId);
        if (userEmitters == null) return false;
        synchronized (userEmitters) {
            return userEmitters.values.get(session) == emitter;
        }
    }

    /** 先摘除旧连接，再发送终止事件；关闭回调不能删除之后注册的新连接。 */
    private void revokeEmitter(Long userId, SessionKey session, SseEmitter emitter, String currentToken) {
        try {
            removeEmitter(userId, session, emitter);
        } finally {
            sendInvalidationAndClose(userId, emitter, currentToken);
        }
    }

    private void sendInvalidationAndClose(Long userId, SseEmitter emitter, String currentToken) {
        try {
            emitter.send(SseEmitter.event().name("auth-invalidated").data(JsonUtils.toStr(Map.of(
                    "uid", userId.toString(),
                    "reason", currentToken == null ? "SESSION_EXPIRED" : "SESSION_REPLACED"))));
        } catch (Exception exception) {
            log.info("SSE session invalidation write failed, uid={}", userId);
        } finally {
            closeEmitter(emitter, false);
        }
    }

    /**
     * 仅移除仍指向当前 emitter 的连接，避免旧连接回调误删新连接。
     */
    private void removeEmitter(Long userId, SessionKey session, SseEmitter emitter) {
        UserEmitters userEmitters = USER_TOKEN_EMITTERS.get(userId);
        if (userEmitters == null) {
            return;
        }
        boolean disconnected;
        synchronized (userEmitters) {
            if (userEmitters.values.get(session) != emitter) {
                return;
            }
            userEmitters.values.remove(session);
            disconnected = !userEmitters.replacing && userEmitters.values.isEmpty();
            removeUserEmittersIfEmpty(userId, userEmitters);
        }
        if (disconnected) {
            applicationEventPublisher.publishEvent(new SseConnectionChangedEvent(userId, false));
        }
    }

    /**
     * 仅移除仍指向当前连接表的空用户项，替换旧连接期间保留外层映射。
     */
    private void removeUserEmittersIfEmpty(Long userId, UserEmitters userEmitters) {
        if (!userEmitters.replacing && userEmitters.values.isEmpty()) {
            USER_TOKEN_EMITTERS.remove(userId, userEmitters);
        }
    }

    /**
     * 安全关闭 emitter。响应可能已经被容器回收，关闭失败不应影响其他连接。
     */
    private void closeEmitter(SseEmitter emitter, boolean sendDisconnectedEvent) {
        if (sendDisconnectedEvent) {
            try {
                emitter.send(SseEmitter.event().comment("disconnected"));
            } catch (Exception ignore) {
                // 关闭通知发送失败说明客户端已经断开，不再调用 complete。
                return;
            }
        }
        try {
            emitter.complete();
        } catch (Exception ignore) {
            // 应用关闭时响应可能已经被 Tomcat 回收。
        }
    }

    /**
     * 应用启动后允许建立 SSE 连接。
     */
    @Override
    public void start() {
        running.set(true);
    }

    /**
     * 在 WebServer 优雅停机前关闭 SSE，避免长连接阻塞 Tomcat 停机等待。
     */
    @Override
    public void stop() {
        if (running.getAndSet(false)) {
            log.info("SSE 管理器开始停机，关闭本机全部连接");
            // 专用池在 WebServer 停机前中断，配置中的销毁方法仅作兜底。
            pushExecutor.shutdownNow();
            heartbeatExecutor.shutdownNow();
            // 停机时不再向客户端写通知，避免慢客户端阻塞应用退出。
            disconnectAll();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 使用高于 webServerGracefulShutdown 的阶段值，确保 SSE 先于 WebServer 关闭。
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    /**
     * 发布SSE订阅消息
     *
     * @param sseMessageDto 要发布的SSE消息对象
     */
    public void publishMessage(SseMessageDto sseMessageDto) {
        SseMessageDto broadcastMessage = new SseMessageDto();
        broadcastMessage.setMessage(sseMessageDto.getMessage());
        broadcastMessage.setUserIds(sseMessageDto.getUserIds());
        mqProducer.sendSecureMsg(MQConstant.SSE_TOPIC, broadcastMessage);
        log.info("SSE发送主题订阅消息topic:{} session keys:{} message:{}",
            MQConstant.SSE_TOPIC, sseMessageDto.getUserIds(), sseMessageDto.getMessage());
    }

    /**
     * 向所有的用户发布订阅的消息(群发)
     *
     * @param message 要发布的消息内容
     */
    public void publishAll(String message) {
        SseMessageDto broadcastMessage = new SseMessageDto();
        broadcastMessage.setMessage(message);
        mqProducer.sendSecureMsg(MQConstant.SSE_TOPIC, broadcastMessage);
        log.info("SSE发送主题订阅消息topic:{} message:{}", MQConstant.SSE_TOPIC, message);
    }
}
