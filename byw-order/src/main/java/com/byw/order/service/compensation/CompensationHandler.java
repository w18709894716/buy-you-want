package com.byw.order.service.compensation;

import com.byw.order.entity.CompensationTask;

/**
 * 单个补偿动作的执行体。
 *
 * <p><b>实现必须是幂等的</b>：同一个任务可能被执行多次（重试、崩溃后回收重放）。
 * 幂等靠下游业务键保证（退款 refNo、售后单号、只翻 status=1 的记录、库存 CAS），
 * 不要在这里做"先查再调"的本地判断 —— 那既不可靠也挡不住重复。
 *
 * <p>抛异常 = 本次失败，由调用方决定退避重试还是转需人工；正常返回 = 成功。
 */
public interface CompensationHandler {

    CompensationType type();

    /**
     * 执行一次。失败直接抛异常，异常信息会截断后写入 {@code last_error} 供人工排查。
     */
    void execute(CompensationTask task) throws Exception;
}
