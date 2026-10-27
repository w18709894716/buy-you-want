package com.byw.order.service;

import com.byw.order.entity.CompensationTask;
import com.byw.order.service.compensation.CompensationType;

/**
 * 后置补偿任务（本地消息表）的登记与执行入口。
 *
 * <p>解决的是这样一类问题：资金动作必须在本地事务提交后才调远端（放进事务里会被"远端已成功 + 本地回滚"
 * 放大成重复退款），但"提交后直接调、失败只打日志"又会在进程崩溃时静默丢失，且没有任何状态可对账。
 * 这里把待办动作随业务数据**在同一个本地事务内**落库，提交后立刻试一次，失败交给定时任务重试。
 */
public interface CompensationService {

    /**
     * 在**当前业务事务内**登记任务（加入调用方事务）。
     *
     * <p>用于"业务成功就必须执行的动作"：业务回滚则任务一起回滚，业务提交则任务必定落库。
     * 不在事务中调用会抛 {@link IllegalStateException} —— 这是硬约束，
     * 在外面登记等于又回到"提交了但任务可能没写"的老问题。
     */
    void enqueueInCurrentTx(CompensationType type, String bizKey,
                            String orderNo, String afterSaleNo, Object payload);

    /**
     * 独立事务登记（REQUIRES_NEW）。
     *
     * <p>只用于"业务已经回滚、但副作用必须补做"的场景（下单失败撤券/撤预占）：
     * 那时没有业务事务可加入，任务必须自己提交。
     */
    void enqueueStandalone(CompensationType type, String bizKey,
                           String orderNo, String afterSaleNo, Object payload);

    /** 事务提交后立即驱动一次；失败不抛，留给定时任务重试 */
    void executeNow(CompensationType type, String bizKey);

    /** 执行一条已由调用方 CAS 认领（status: 0→3）的任务，并回写结果 */
    void executeClaimed(CompensationTask task);
}
