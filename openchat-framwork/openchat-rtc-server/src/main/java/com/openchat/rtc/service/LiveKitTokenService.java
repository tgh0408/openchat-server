package com.openchat.rtc.service;

import com.openchat.rtc.autoconfigure.LiveKitProperties;
import com.openchat.rtc.domain.RtcJoinCredential;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanPublishSources;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * 基于 LiveKit Server SDK 的 Join Token 适配器。
 *
 * <p>本类只组装 SDK 的 {@link AccessToken} 和 {@link io.livekit.server.VideoGrant}；
 * 呼叫状态、权限、持久化和事件投递仍由业务服务负责。</p>
 */
public final class LiveKitTokenService {
    private final LiveKitProperties properties;

    public LiveKitTokenService(LiveKitProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("LiveKit 配置不能为空");
        }
        validateProperties(properties);
        this.properties = properties;
    }

    public RtcJoinCredential issue(
            String roomName,
            String identity,
            String displayName,
            boolean videoAllowed) {
        if (roomName == null || roomName.isBlank()
                || identity == null || identity.isBlank()
                || displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("LiveKit Token 参数无效");
        }
        Duration ttl = Duration.ofSeconds(properties.getTokenTtlSeconds());
        Instant expiresAt = Instant.now().plus(ttl);
        AccessToken accessToken = new AccessToken(properties.getApiKey(), properties.getApiSecret());
        accessToken.setIdentity(identity);
        accessToken.setName(displayName);
        accessToken.setTtl(ttl.toMillis());
        accessToken.setExpiration(Date.from(expiresAt));
        accessToken.addGrants(
                new RoomJoin(true),
                new RoomName(roomName),
                new CanPublishSources(videoAllowed
                        ? List.of("microphone", "camera")
                        : List.of("microphone")),
                new CanSubscribe(true),
                new CanPublishData(true));
        return new RtcJoinCredential(
                properties.getServerUrl(),
                roomName,
                accessToken.toJwt(),
                expiresAt,
                videoAllowed);
    }

    public RtcJoinCredential issue(
            String roomName,
            String identity,
            String displayName) {
        return issue(roomName, identity, displayName, true);
    }

    private void validateProperties(LiveKitProperties properties) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()
                || properties.getApiSecret() == null || properties.getApiSecret().isBlank()
                || properties.getServerUrl() == null || properties.getServerUrl().isBlank()
                || properties.getTokenTtlSeconds() <= 0) {
            throw new IllegalStateException("LiveKit 配置无效");
        }
    }
}
