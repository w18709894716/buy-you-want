package com.byw.settle.service;

import com.byw.api.settle.dto.SettleRecordDTO;
import com.byw.common.core.result.PageResult;

/**
 * 结算单服务：收货触发结算单生成（冷静期冻结）、T+N 到期入账、结算单查询。
 */
public interface SettleService {

    /** 收货触发：为已收货子订单生成结算单并计入待结算(pending)。幂等（按 orderNo 去重）。 */
    void settleOnReceive(String orderNo);

    /** T+N 到期：将已过冷静期的待结算单转入可用余额。返回本次入账笔数。 */
    int settleDueRecords();

    /**
     * 退款联动冲减结算：买家退款成功后按商家应得占比把这笔钱从结算里扣回来。
     *
     * <p>不冲减的后果是"同一笔钱付两次"——买家拿到退款，冷静期一到
     * {@link #settleDueRecords()} 仍把全额打进商家可用余额。
     *
     * @param orderNo      子订单号（结算单以子订单为维度）
     * @param refundAmount 本次退给买家的金额
     * @param refNo        业务幂等键（售后单号）；重复调用只冲减一次
     */
    void adjustOnRefund(String orderNo, java.math.BigDecimal refundAmount, String refNo);

    /** 结算单分页查询（shopId 为空表示平台查全部）。 */
    PageResult<SettleRecordDTO> listSettleRecords(Long shopId, Integer status, Integer pageNum, Integer pageSize);
}
