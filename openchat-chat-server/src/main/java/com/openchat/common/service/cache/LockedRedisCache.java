package com.openchat.common.service.cache;

import com.openchat.common.service.LockService;
import com.openchat.common.utils.AssertUtil;
import com.openchat.common.utils.JsonUtils;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 每个缓存键独立加锁；回填和删除在 Lua 内校验持有者，不维护数据版本或跨缓存预加载。
 * 事务内只回源，避免未提交数据及旧事务快照进入共享缓存。
 */
public abstract class LockedRedisCache<K, V> implements BatchCache<K, V> {
    private final long TTL_SECONDS = getTtlSeconds();
    private static final long LOCK_WAIT_SECONDS = 3L;
    private static final String READ = "return redis.call('GET',KEYS[1])";
    private static final String FILL =
            "if redis.call('HEXISTS',KEYS[1],ARGV[1])==0 then return 0 end; "
                    + "redis.call('SET',KEYS[2],ARGV[2],'EX',ARGV[3]); return 1";
    private static final String DELETE =
            "if redis.call('HEXISTS',KEYS[1],ARGV[1])==0 then return 0 end; "
                    + "redis.call('DEL',KEYS[2]); return 1";

    private final LockService locks;
    private final RedissonClient redis;

    protected LockedRedisCache(LockService locks) {
        this.locks = Objects.requireNonNull(locks);
        this.redis = locks.getRedissonClient();
    }

    public static <K, V> LockedRedisCache<K, V> create(
            LockService locks, Function<K, String> keyFactory,
            Function<List<K>, Map<K, V>> loader, Function<String, V> decoder) {
        return new LockedRedisCache<>(locks) {
            @Override
            protected long getTtlSeconds() {
                return 300L;
            }

            @Override
            protected String getKey(K key) {
                return keyFactory.apply(key);
            }

            @Override
            protected Map<K, V> load(List<K> keys) {
                return loader.apply(keys);
            }

            @Override
            protected V decode(K key, String payload) {
                return decoder.apply(payload);
            }
        };
    }

    protected abstract long getTtlSeconds();
    protected abstract String getKey(K key);
    protected abstract Map<K, V> load(List<K> keys);
    protected abstract V decode(K key, String payload);

    protected boolean eligible(K key, V value) {
        return true;
    }

    protected boolean cacheable(K key, V value) {
        return true;
    }

    @Override
    public V get(K key) {
        return getBatch(Collections.singletonList(key)).get(key);
    }

    @Override
    public V get(K key, String error) {
        V value = get(key);
        AssertUtil.isNonNull(value, JsonUtils.toStr(key), error);
        return value;
    }

    @Override
    public Map<K, V> getBatch(List<K> keys) {
        Map<K, V> result = new LinkedHashMap<>();
        if (keys == null || keys.isEmpty()) return result;
        List<K> requested = keys.stream().distinct().toList();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            Map<K, V> loaded = load(requested);
            requested.forEach(key -> result.put(key, loaded.get(key)));
            return result;
        }

        Map<K, String> missing = new LinkedHashMap<>();
        for (K key : requested) {
            String dataKey = dataKey(key);
            if (!readCached(key, dataKey, result)) missing.put(key, dataKey);
        }
        if (!missing.isEmpty()) {
            List<String> names = missing.values().stream().map(this::lockKey).toList();
            locks.lockAll(names, LOCK_WAIT_SECONDS, TimeUnit.SECONDS, owners -> {
                Map<K, String> pending = new LinkedHashMap<>();
                for (Map.Entry<K, String> entry : missing.entrySet()) {
                    if (!readCached(entry.getKey(), entry.getValue(), result)) {
                        pending.put(entry.getKey(), entry.getValue());
                    }
                }
                if (pending.isEmpty()) return null;
                Map<K, V> loaded = load(new ArrayList<>(pending.keySet()));
                for (Map.Entry<K, String> entry : pending.entrySet()) {
                    K key = entry.getKey();
                    String dataKey = entry.getValue();
                    V value = loaded.get(key);
                    if (value != null && !eligible(key, value)) value = null;
                    String payload = null;
                    if (value != null && cacheable(key, value)) payload = JsonUtils.toStr(value);
                    mutate(dataKey, owners.get(lockKey(dataKey)), payload);
                    result.put(key, value);
                }
                return null;
            });
        }
        Map<K, V> ordered = new LinkedHashMap<>();
        requested.forEach(key -> ordered.put(key, result.get(key)));
        return ordered;
    }

    /** 锁与数据使用同一客户端、数据库和 StringCodec；READ_WRITE 保证从主节点读取。 */
    private boolean readCached(K key, String dataKey, Map<K, V> result) {
        String raw = redis.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE, READ, RScript.ReturnType.VALUE, List.of(dataKey));
        if (raw == null) return false;
        V value = decode(key, raw);
        if (value == null || !eligible(key, value) || !cacheable(key, value)) return false;
        result.put(key, value);
        return true;
    }

    /** 归属校验与写入不可拆分；失去锁后丢弃本次查询结果，调用方重试时重新回源。 */
    private void mutate(String dataKey, String owner, String payload) {
        String name = lockKey(dataKey);
        Long changed;
        if (payload == null) {
            changed = redis.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE,
                    DELETE, RScript.ReturnType.LONG, List.of(name, dataKey), owner);
        } else {
            changed = redis.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE,
                    FILL, RScript.ReturnType.LONG, List.of(name, dataKey),
                    owner, payload, String.valueOf(TTL_SECONDS));
        }
        if (!Objects.equals(changed, 1L)) {
            throw new IllegalStateException("缓存锁归属已失效，请重试");
        }
    }

    /** 使用独立命名空间，不将旧版本快照误解码为业务值。 */
    private String dataKey(K key) {
        String name = getKey(key) + ":locked";
        int start = name.indexOf('{');
        int end = name.indexOf('}', start + 1);
        if (start < 0 || end <= start + 1) {
            throw new IllegalArgumentException("缓存键必须包含非空 Redis Cluster hash tag");
        }
        return name;
    }

    private String lockKey(String dataKey) {
        return dataKey + ":lock";
    }

    @Override
    public void deleteCache(K key) {
        String dataKey = dataKey(key);
        String name = lockKey(dataKey);
        locks.lockAll(List.of(name), LOCK_WAIT_SECONDS, TimeUnit.SECONDS, owners -> {
            mutate(dataKey, owners.get(name), null);
            return null;
        });
    }

    /** 单个键失效失败不阻止其余键的清理，最终统一向调用方报告失败。 */
    @Override
    public void deleteBatchCache(List<K> keys) {
        if (keys == null) return;
        RuntimeException failure = null;
        for (K key : keys.stream().distinct().toList()) {
            try {
                deleteCache(key);
            } catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    public void invalidateAfterCommit(K key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteCache(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteCache(key);
            }
        });
    }
}
