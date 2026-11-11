package com.openchat.webrtc.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 群视频席位协调器。
 *
 * <p>使用单 key 有序集合和 Lua 原子领取，多个聊天服务实例共享同一组最多 9 个视频席位。
 * 席位是租约，客户端停止续租或节点异常后会自动过期，不依赖单机内存。</p>
 */
@Service
@RequiredArgsConstructor
public class RtcVideoSeatService {
    private static final int MAX_VIDEO_SEATS = 9;
    private static final Duration LEASE = Duration.ofSeconds(90);

    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT =
            new DefaultRedisScript<>("""
                    local now = tonumber(ARGV[1])
                    local expires = tonumber(ARGV[2])
                    local member = ARGV[3]
                    redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
                    if redis.call('ZSCORE', KEYS[1], member) then
                        redis.call('ZADD', KEYS[1], expires, member)
                        redis.call('EXPIRE', KEYS[1], ARGV[4])
                        return 1
                    end
                    if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[5]) then
                        return 0
                    end
                    redis.call('ZADD', KEYS[1], expires, member)
                    redis.call('EXPIRE', KEYS[1], ARGV[4])
                    return 1
                    """, Long.class);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>("""
                    local removed = redis.call('ZREM', KEYS[1], ARGV[1])
                    if redis.call('ZCARD', KEYS[1]) == 0 then
                        redis.call('DEL', KEYS[1])
                    end
                    return removed
                    """, Long.class);

    private final StringRedisTemplate redis;

    /**
     * 领取或续租一个群视频席位。
     */
    public boolean acquire(String callId, Long uid) {
        long now = Instant.now().getEpochSecond();
        long expires = now + LEASE.toSeconds();
        Long result = redis.execute(
                ACQUIRE_SCRIPT,
                List.of(key(callId)),
                String.valueOf(now),
                String.valueOf(expires),
                String.valueOf(uid),
                String.valueOf(LEASE.toSeconds()),
                String.valueOf(MAX_VIDEO_SEATS));
        return Long.valueOf(1).equals(result);
    }

    /**
     * 释放当前用户的视频席位。
     */
    public void release(String callId, Long uid) {
        redis.execute(RELEASE_SCRIPT, List.of(key(callId)), String.valueOf(uid));
    }

    private String key(String callId) {
        if (callId == null || callId.isBlank()) {
            throw new IllegalArgumentException("无效的 RTC 呼叫 ID");
        }
        return "openchat:rtc:video-seat:{" + callId + "}";
    }
}
