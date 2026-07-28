package com.byw.admin.controller;

import com.byw.api.settle.SettleFeignClient;
import com.byw.api.settle.dto.CommissionRuleDTO;
import com.byw.api.settle.dto.SettleRecordDTO;
import com.byw.api.settle.dto.WithdrawRecordDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireAdmin;
import com.byw.common.security.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 平台端结算管理：佣金规则增删改查、全部结算单/提现单查询、提现审批。
 */
@RestController
@RequestMapping("/admin/settle")
@RequireAdmin
@RequiredArgsConstructor
public class AdminSettleController {

    private final SettleFeignClient settleFeignClient;

    // ========== 佣金规则 ==========

    @GetMapping("/commission/list")
    public R<List<CommissionRuleDTO>> listCommissionRules() {
        return settleFeignClient.listCommissionRules();
    }

    @PostMapping("/commission/save")
    public R<Boolean> saveCommissionRule(@RequestBody CommissionRuleDTO dto) {
        return settleFeignClient.saveCommissionRule(dto);
    }

    @DeleteMapping("/commission/{id}")
    public R<Boolean> deleteCommissionRule(@PathVariable Long id) {
        return settleFeignClient.deleteCommissionRule(id);
    }

    // ========== 结算单监管 ==========

    @GetMapping("/record/list")
    public R<PageResult<SettleRecordDTO>> listSettleRecords(@RequestParam(required = false) Long shopId,
                                                            @RequestParam(required = false) Integer status,
                                                            @RequestParam(defaultValue = "1") Integer pageNum,
                                                            @RequestParam(defaultValue = "10") Integer pageSize) {
        return settleFeignClient.listSettleRecords(shopId, status, pageNum, pageSize);
    }

    // ========== 提现审批 ==========

    @GetMapping("/withdraw/list")
    public R<PageResult<WithdrawRecordDTO>> listWithdraws(@RequestParam(required = false) Long shopId,
                                                          @RequestParam(required = false) Integer status,
                                                          @RequestParam(defaultValue = "1") Integer pageNum,
                                                          @RequestParam(defaultValue = "10") Integer pageSize) {
        return settleFeignClient.listWithdraws(shopId, status, pageNum, pageSize);
    }

    @PostMapping("/withdraw/audit")
    public R<Boolean> auditWithdraw(@RequestParam Long withdrawId,
                                    @RequestParam Boolean pass,
                                    @RequestParam(required = false) String rejectReason) {
        return settleFeignClient.auditWithdraw(withdrawId, pass, rejectReason, UserContext.getUsername());
    }
}
