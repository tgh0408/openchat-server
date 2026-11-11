package com.openchat.webrtc.service;

import com.openchat.common.exception.BusinessException;
import com.openchat.common.utils.JsonUtils;
import com.openchat.transaction.annotation.SecureInvoke;
import com.openchat.webrtc.domain.vo.resp.RtcCameraState;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

/** 按通话共享摄像头状态；单 key Lua 保证跨节点比较版本与写入的原子性。 */
@Service
@RequiredArgsConstructor
public class RtcCameraStateService {
    private static final String TTL_SECONDS = "86400";
    private static final DefaultRedisScript<Long> UPDATE = new DefaultRedisScript<>("""
            if redis.call('TYPE', KEYS[1]).ok == 'string' then return -2 end
            local previous = redis.call('HGET', KEYS[1], ARGV[1])
            local version = 0
            if previous then version = cjson.decode(previous).version end
            if version ~= tonumber(ARGV[2]) then return -1 end
            local nextVersion = version + 1
            redis.call('HSET', KEYS[1], ARGV[1],
                cjson.encode({enabled = ARGV[3] == 'true', version = nextVersion}))
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            return nextVersion
            """, Long.class);
    private static final DefaultRedisScript<String> READ = new DefaultRedisScript<>("""
            if redis.call('TYPE', KEYS[1]).ok == 'string' then return '{}' end
            local values = redis.call('HGETALL', KEYS[1])
            local result = {}
            for i = 1, #values, 2 do result[values[i]] = cjson.decode(values[i + 1]) end
            if #values > 0 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return cjson.encode(result)
            """, String.class);
    private static final DefaultRedisScript<Long> CLOSE = new DefaultRedisScript<>("""
            if redis.call('TYPE', KEYS[1]).ok == 'string' then return 1 end
            redis.call('SET', KEYS[1], 'closed', 'EX', ARGV[1])
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;

    /** 读取并延长活跃通话的状态保留时间；不使用节点内存作为权威快照。 */
    public Map<String, RtcCameraState> get(String callId) {
        String json = redis.execute(READ, List.of(key(callId)), TTL_SECONDS);
        return JsonUtils.toObj(json, new TypeReference<Map<String, RtcCameraState>>() {});
    }

    /** 条件写入本人状态；冲突直接失败，不重读并覆盖其他节点的新状态。 */
    public RtcCameraState update(String callId, Long uid, boolean enabled, long expectedVersion) {
        long version = redis.execute(UPDATE, List.of(key(callId)), uid.toString(),
                Long.toString(expectedVersion), Boolean.toString(enabled), TTL_SECONDS);
        if (version == -2) throw new BusinessException("通话已结束");
        if (version < 1) throw new BusinessException("摄像头状态已变化，请重新同步");
        return new RtcCameraState(enabled, version);
    }

    /**
     * 终态事务提交后释放成员状态；可靠调用负责失败重试。
     * 保留定期过期的单值标记，拒绝其他节点已通过通话校验的迟到写入；读取和重复清理不续期。
     */
    @SecureInvoke(async = true, maxRetryTimes = 10)
    public void close(String callId) {
        Long result = redis.execute(CLOSE, List.of(key(callId)), TTL_SECONDS);
        if (result != 1) throw new BusinessException("摄像头状态清理失败");
    }

    /** 同一通话所有成员只使用一个 Redis Cluster hash slot。 */
    private String key(String callId) {
        return "openchat:rtc:camera:{" + callId + "}";
    }
}
