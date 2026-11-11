package com.openchat.common.event.listener;

import com.openchat.common.event.SseLoginEvent;
import com.openchat.user.service.IpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** 登录 IP 更新的事件入口；每日限制和提交后的异步详情补齐由 IP 服务负责。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SseLoginListener {
    private final IpService ipService;

    @EventListener
    public void onLogin(SseLoginEvent event) {
        try {
            // 建连入口没有外层事务，普通监听器直接进入 IP 服务的短事务。
            ipService.refreshIp(event.getUid(), event.getIp());
        } catch (RuntimeException exception) {
            // IP 是附加信息，更新失败不影响已受理的连接或其他事件监听器。
            log.warn("Failed to refresh SSE login IP, uid={}", event.getUid(), exception);
        }
    }
}
