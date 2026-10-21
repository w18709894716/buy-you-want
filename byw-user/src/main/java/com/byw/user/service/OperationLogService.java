package com.byw.user.service;

import com.byw.api.user.dto.OperationLogDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.log.dto.OperationLogMessage;

/**
 * 操作审计日志：落库（MQ 消费）与查询（管理端）。
 */
public interface OperationLogService {

    /** 保存一条审计日志（由 MQ 消费者调用） */
    void save(OperationLogMessage message);

    /** 分页查询，条件全部可空 */
    PageResult<OperationLogDTO> page(int pageNum, int pageSize, String module, Long operatorId,
                                     Integer success, String startTime, String endTime);
}
