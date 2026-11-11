package com.openchat.rtc.domain;

import java.time.Instant;

/**
 * LiveKit 客户端连接凭证。
 *
 * <p>该对象是 OpenChat 的 HTTP 响应模型，不替代 LiveKit SDK 的
 * {@link io.livekit.server.AccessToken}。</p>
 */
public record RtcJoinCredential(
        String serverUrl,
        String roomName,
        String token,
        Instant expiresAt,
        boolean videoAllowed) {
}
