package com.byw.admin.controller;

import com.byw.api.user.OperationLogFeignClient;
import com.byw.api.user.dto.OperationLogDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequirePerm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台端：操作审计日志查询（转发 byw-user 的内部契约）。
 *
 * <p>数据来自各服务 {@code @OperationLog} 切面经 RocketMQ 异步写入的 {@code t_operation_log}。
 * 只读接口，权限沿用菜单权限标识 {@code sys:log}。
 */
@Tag(name = "操作日志", description = "管理端操作审计日志查询")
@RestController
@RequestMapping("/admin/system/operation-log")
@RequirePerm("sys:log")
@RequiredArgsConstructor
public class AdminOperationLogController {

    private final OperationLogFeignClient operationLogFeignClient;

    @Operation(summary = "分页查询操作日志")
    @GetMapping
    public R<PageResult<OperationLogDTO>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) Integer success,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return operationLogFeignClient.page(pageNum, pageSize, module, operatorId, success, startTime, endTime);
    }
}
