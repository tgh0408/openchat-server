package com.openchat.common.scheduled;

import com.openchat.chat.service.RoomActivityService;
import com.openchat.common.config.ThreadPoolConfig;
import com.openchat.common.sse.config.SseProperties;
import com.openchat.common.sse.core.SseEmitterManager;
import com.openchat.transaction.service.SecureInvokeService;
import com.openchat.webrtc.service.RtcInvitationExpiryTask;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@EnableScheduling
@Component
public class GlobalSchedule {
    private final RoomActivityService roomActivityService;
    private final SecureInvokeService secureInvokeService;
    private final RtcInvitationExpiryTask rtcInvitationExpiryTask;
    private final ObjectProvider<SseEmitterManager> sseEmitterManager;
    private final ObjectProvider<SseProperties> sseProperties;
    private final ObjectProvider<ScheduledExecutorService> heartbeatExecutor;

    public GlobalSchedule(
            RoomActivityService roomActivityService,
            SecureInvokeService secureInvokeService,
            RtcInvitationExpiryTask rtcInvitationExpiryTask,
            ObjectProvider<SseEmitterManager> sseEmitterManager,
            ObjectProvider<SseProperties> sseProperties,
            @Qualifier(ThreadPoolConfig.SSE_HEARTBEAT_EXECUTOR)
            ObjectProvider<ScheduledExecutorService> heartbeatExecutor) {
        this.roomActivityService = roomActivityService;
        this.secureInvokeService = secureInvokeService;
        this.rtcInvitationExpiryTask = rtcInvitationExpiryTask;
        this.sseEmitterManager = sseEmitterManager;
        this.sseProperties = sseProperties;
        this.heartbeatExecutor = heartbeatExecutor;
    }

    /** 集中注册心跳，仍使用专用单线程，避免慢连接阻塞业务定时任务。 */
    @PostConstruct
    public void scheduleSseHeartbeats() {
        sseEmitterManager.ifAvailable(manager -> {
            int interval = sseProperties.getObject().getHeartbeatIntervalSeconds();
            if (interval <= 0) throw new IllegalArgumentException("Invalid SSE heartbeat interval");
            heartbeatExecutor.getObject().scheduleAtFixedRate(
                    manager::sendHeartbeats, interval, interval, TimeUnit.SECONDS);
        });
    }

    @Scheduled(fixedDelayString = "${openchat.chat.room-activity.poll-ms:500}")
    public void flush(){
        roomActivityService.flush();
    }

    @Scheduled(fixedDelay = 5000)
    public void retry(){
        secureInvokeService.retry();
    }

    /** 发送端在一分钟主动断开，后台仅补偿截止后仍未处理的邀请。 */
    @Scheduled(fixedDelay = 5000)
    public void expireRtcInvitations() {
        rtcInvitationExpiryTask.expireInvitations();
    }

}
