package com.byw.user.feign;

import com.byw.api.user.OperationLogFeignClient;
import com.byw.api.user.dto.OperationLogDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.InternalApi;
import com.byw.user.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作审计日志内部契约实现（仅服务间调用，需内部签名）。
 */
@RestController
@RequestMapping("/feign/operation-log")
@RequiredArgsConstructor
@InternalApi
public class OperationLogFeignImpl implements OperationLogFeignClient {

    private final OperationLogService operationLogService;

    @Override
    @GetMapping("/page")
    public R<PageResult<OperationLogDTO>> page(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                               @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                               @RequestParam(value = "module", required = false) String module,
                                               @RequestParam(value = "operatorId", required = false) Long operatorId,
                                               @RequestParam(value = "success", required = false) Integer success,
                                               @RequestParam(value = "startTime", required = false) String startTime,
                                               @RequestParam(value = "endTime", required = false) String endTime) {
        return R.ok(operationLogService.page(pageNum, pageSize, module, operatorId, success, startTime, endTime));
    }
}
