package com.byw.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后置补偿任务（本地消息表）。
 *
 * <p>业务事务内登记，事务提交后立刻驱动一次，失败由定时任务按指数退避重试。
 * 存在的意义是让"资金类后置动作"在崩溃时留下可被扫到的状态，而不是只留一行日志。
 */
@Data
@TableName("t_compensation_task")
public class CompensationTask {

    /** 待执行：到期会被调度器扫描 */
    public static final int STATUS_PENDING = 0;
    /** 已完成 */
    public static final int STATUS_DONE = 1;
    /** 需人工：超过最大重试次数，或登记的参数已无法执行 */
    public static final int STATUS_MANUAL = 2;
    /** 执行中：已被某个执行方 CAS 认领，进程崩溃会由调度器回收回待执行 */
    public static final int STATUS_RUNNING = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** {@link com.byw.order.service.compensation.CompensationType} 的名称 */
    private String taskType;

    /** 幂等键，同一 taskType 内唯一；重复登记撞唯一键即视为已登记 */
    private String bizKey;

    private String orderNo;

    private String afterSaleNo;

    /** 执行参数 JSON 快照，避免执行时回查业务表（业务表可能已经变了） */
    private String payload;

    /** 0待执行 1已完成 2需人工 3执行中 */
    private Integer status;

    private Integer retryCount;

    private LocalDateTime nextRetryAt;

    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
