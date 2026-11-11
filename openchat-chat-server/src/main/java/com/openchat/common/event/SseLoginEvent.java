package com.openchat.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/** 已鉴权用户的登录 SSE 已被受理；只保存本次连接的身份和 IP 快照。 */
@Getter
public class SseLoginEvent extends ApplicationEvent {
    private final Long uid;
    private final String ip;

    public SseLoginEvent(Object source, Long uid, String ip) {
        super(source);
        this.uid = uid;
        this.ip = ip;
    }
}
