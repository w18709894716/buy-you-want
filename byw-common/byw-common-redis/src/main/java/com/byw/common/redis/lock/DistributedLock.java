package com.byw.common.redis.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
public class DistributedLock {

    private final RedissonClient redissonClient;

    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(waitTime, leaseTime, unit);
            if (!acquired) {
                throw new RuntimeException("获取分布式锁失败: " + lockKey);
            }
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取分布式锁被中断: " + lockKey, e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public void executeWithLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit, Runnable runnable) {
        executeWithLock(lockKey, waitTime, leaseTime, unit, () -> {
            runnable.run();
            return null;
        });
    }

    public <T> T executeWithLock(String lockKey, Supplier<T> supplier) {
        return executeWithLock(lockKey, 5, 30, TimeUnit.SECONDS, supplier);
    }

    public void executeWithLock(String lockKey, Runnable runnable) {
        executeWithLock(lockKey, 5, 30, TimeUnit.SECONDS, runnable);
    }
}
