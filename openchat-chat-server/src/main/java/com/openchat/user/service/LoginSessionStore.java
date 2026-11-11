package com.openchat.user.service;

import com.openchat.common.constant.RedisKey;
import com.openchat.common.utils.JsonUtils;
import com.openchat.user.domain.enums.TerminalEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 集群内同账号、同平台唯一登录令牌。保留 JSON 字符串格式，不缓存到本机。
 */
@Component
@RequiredArgsConstructor
public class LoginSessionStore {
    private static final Duration TOKEN_TTL = Duration.ofDays(3);
    private static final long TOKEN_RENEWAL_THRESHOLD_SECONDS = Duration.ofDays(1).toSeconds();
    private static final DefaultRedisScript<Long> REVOKE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            return redis.call('DEL', KEYS[1])
            """, Long.class);
    private static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return -2 end
            local ttl = redis.call('TTL', KEYS[1])
            if ttl >= 0 and ttl < tonumber(ARGV[2]) then
                if redis.call('EXPIRE', KEYS[1], ARGV[3]) == 1 then
                    return tonumber(ARGV[3])
                end
                return -2
            end
            return ttl
            """, Long.class);

    private final StringRedisTemplate redis;

    /** 单次 SET 同时替换令牌和 TTL；写入失败必须使登录失败。 */
    public void replace(Long uid, TerminalEnum terminal, String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Missing login token");
        redis.opsForValue().set(key(uid, terminal), JsonUtils.toStr(token), TOKEN_TTL);
    }

    public String currentToken(Long uid, TerminalEnum terminal) {
        String value = redis.opsForValue().get(key(uid, terminal));
        return value == null ? null : JsonUtils.toObj(value, String.class);
    }

    /** 原子核对本次令牌后删除，迟到退出不能撤销同平台的新登录。 */
    public boolean revoke(Long uid, TerminalEnum terminal, String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Missing login token");
        Long removed = redis.execute(REVOKE, List.of(key(uid, terminal)), JsonUtils.toStr(token));
        return removed == 1L;
    }

    /** 比较与续期在同一脚本内完成，旧请求不能续期新会话或复活旧令牌。 */
    public Long renew(Long uid, TerminalEnum terminal, String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Missing login token");
        Long ttlSeconds = redis.execute(RENEW, List.of(key(uid, terminal)), JsonUtils.toStr(token),
                Long.toString(TOKEN_RENEWAL_THRESHOLD_SECONDS), Long.toString(TOKEN_TTL.toSeconds()));
        return ttlSeconds >= 0 ? ttlSeconds : null;
    }

    private String key(Long uid, TerminalEnum terminal) {
        if (uid == null || uid <= 0 || terminal == null) throw new IllegalArgumentException("Invalid login identity");
        return RedisKey.getKey(RedisKey.USER_TOKEN_STRING, uid, terminal.name());
    }
}
