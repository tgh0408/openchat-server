package com.openchat.common.event;

/** 当前实例的 SSE 用户连接状态发生变化。 */
public record SseConnectionChangedEvent(Long userId, boolean connected) {
}
