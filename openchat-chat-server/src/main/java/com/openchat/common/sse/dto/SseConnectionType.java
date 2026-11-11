package com.openchat.common.sse.dto;

/** 登录授权后的首次连接与同一授权期间的自动重连。 */
public enum SseConnectionType {
    LOGIN,
    RECONNECT
}
