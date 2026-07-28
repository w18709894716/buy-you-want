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

    /** 结算单分页查询（shopId 为空表示平台查全部）。 */
    PageResult<SettleRecordDTO> listSettleRecords(Long shopId, Integer status, Integer pageNum, Integer pageSize);
}
