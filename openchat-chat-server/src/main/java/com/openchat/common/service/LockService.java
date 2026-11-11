package com.openchat.common.service;

import com.openchat.common.exception.BusinessException;
import com.openchat.common.exception.CommonErrorEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class LockService {
    @Getter
    private final RedissonClient redissonClient;

    public <T> T lock(String name, long timeout, TimeUnit timeUnit, SupplierThrow<T> supplier) {
        return lockAll(List.of(name), timeout, timeUnit, owners -> supplier.get());
    }

    /**
     * 按固定顺序获取锁并共享等待预算；不指定 leaseTime，沿用 Redisson watchdog 续期。
     * 回调必须同步执行，owner 对应 Redisson 当前客户端与线程，仅用于 Redis 原子归属校验。
     */
    @SneakyThrows
    public <T> T lockAll(Collection<String> names, long timeout, TimeUnit timeUnit, LockedTask<T> task) {
        List<RLock> acquired = new ArrayList<>();
        Map<String, String> owners = new LinkedHashMap<>();
        long deadline = System.nanoTime() + timeUnit.toNanos(Math.max(0L, timeout));
        String owner = redissonClient.getId() + ":" + Thread.currentThread().threadId();
        Throwable failure = null;
        try {
            for (String name : names) {
                RLock lock = redissonClient.getLock(name);
                long remaining = Math.max(0L, deadline - System.nanoTime());
                if (!lock.tryLock(remaining, TimeUnit.NANOSECONDS)) {
                    throw new BusinessException(CommonErrorEnum.LOCK_ERROR);
                }
                acquired.add(lock);
                owners.put(name, owner);
            }
            return task.execute(Collections.unmodifiableMap(owners));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failure = exception;
            throw exception;
        } catch (Throwable exception) {
            failure = exception;
            throw exception;
        } finally {
            RuntimeException releaseFailure = null;
            for (int i = acquired.size() - 1; i >= 0; i--) {
                RLock lock = acquired.get(i);
                try {
                    if (lock.isHeldByCurrentThread()) lock.unlock();
                } catch (IllegalMonitorStateException exception) {
                    // 检查后锁仍可能过期；unlock 自身会原子校验归属，不释放其他持有者的锁。
                    log.debug("锁释放前已过期 {}", lock.getName());
                } catch (RuntimeException exception) {
                    if (releaseFailure == null) releaseFailure = exception;
                    else releaseFailure.addSuppressed(exception);
                }
            }
            if (releaseFailure != null) {
                if (failure != null) failure.addSuppressed(releaseFailure);
                else throw releaseFailure;
            }
        }
    }

    public <T> T lock(String name, SupplierThrow<T> supplier){
        return lock(name, -1, TimeUnit.MILLISECONDS, supplier);
    }

    @FunctionalInterface
    public interface LockedTask<T> {
        T execute(Map<String, String> owners) throws Throwable;
    }

    @FunctionalInterface
    public interface SupplierThrow<T> {

        T get() throws Throwable;
    }
}
