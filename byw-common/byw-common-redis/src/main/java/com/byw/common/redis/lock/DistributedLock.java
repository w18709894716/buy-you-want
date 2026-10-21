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

    /**
     * 尝试获取分布式锁执行任务：**抢不到锁直接跳过**（返回 false），不抛异常。
     *
     * <p>用于多实例部署下的 {@code @Scheduled} 任务：同一轮只允许一个节点执行，
     * 其余节点安静跳过即可，不需要把"没抢到锁"当成故障。
     *
     * <p>leaseTime 传 -1 交给 Redisson 看门狗自动续期（默认 30s、每 1/3 续一次），
     * 任务再久也不会中途掉锁；进程崩溃后看门狗停止续期，锁会在 30s 内自动释放。
     */
    public boolean tryExecuteWithLock(String lockKey, long waitTime, TimeUnit unit, Runnable runnable) {
        RLock lock;
        boolean acquired;
        try {
            lock = redissonClient.getLock(lockKey);
            acquired = lock.tryLock(waitTime, -1, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取分布式锁被中断，本节点跳过本轮: lockKey={}", lockKey);
            return false;
        } catch (Exception e) {
            // Redis 不可用不能让对账任务整体停摆：这些任务自身已是幂等的（CAS/唯一键），
            // 因此降级为"不加锁直接执行"（等价于引入锁之前的行为），并明确告警暴露 Redis 故障
            log.warn("获取分布式锁失败，降级为不加锁执行本轮任务: lockKey={}, err={}", lockKey, e.getMessage());
            runnable.run();
            return true;
        }
        if (!acquired) {
            log.info("未抢到分布式锁，本节点跳过本轮: lockKey={}", lockKey);
            return false;
        }
        try {
            runnable.run();
            return true;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public <T> T executeWithLock(String lockKey, Supplier<T> supplier) {
        return executeWithLock(lockKey, 5, 30, TimeUnit.SECONDS, supplier);
    }

    public void executeWithLock(String lockKey, Runnable runnable) {
        executeWithLock(lockKey, 5, 30, TimeUnit.SECONDS, runnable);
    }
}
