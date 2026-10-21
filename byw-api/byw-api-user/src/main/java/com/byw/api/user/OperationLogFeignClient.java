package com.byw.api.user;

import com.byw.api.user.dto.OperationLogDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 操作审计日志契约：byw-user 是日志持有方（t_operation_log 在 byw_user 库），
 * 平台端 byw-admin BFF 经此转发查询。
 */
@FeignClient(name = "byw-user", contextId = "operationLogFeignClient")
public interface OperationLogFeignClient {

    /**
     * 分页查询操作日志。
     *
     * @param startTime 开始时间，形如 {@code 2026-10-16 00:00:00}，可空
     * @param endTime   结束时间，可空
     */
    @GetMapping("/feign/operation-log/page")
    R<PageResult<OperationLogDTO>> page(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                       @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                       @RequestParam(value = "module", required = false) String module,
                                       @RequestParam(value = "operatorId", required = false) Long operatorId,
                                       @RequestParam(value = "success", required = false) Integer success,
                                       @RequestParam(value = "startTime", required = false) String startTime,
                                       @RequestParam(value = "endTime", required = false) String endTime);
}
