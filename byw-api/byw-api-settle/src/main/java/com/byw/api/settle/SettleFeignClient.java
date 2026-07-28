package com.byw.api.settle;

import com.byw.api.settle.dto.BalanceFlowDTO;
import com.byw.api.settle.dto.CommissionRuleDTO;
import com.byw.api.settle.dto.SettleRecordDTO;
import com.byw.api.settle.dto.ShopBalanceDTO;
import com.byw.api.settle.dto.WithdrawApplyDTO;
import com.byw.api.settle.dto.WithdrawRecordDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 结算与分账服务契约（供 byw-merchant / byw-admin BFF 调用）。
 */
@FeignClient(name = "byw-settle", contextId = "settleFeignClient")
public interface SettleFeignClient {

    // ========== 商家余额 ==========

    @GetMapping("/feign/settle/balance/{shopId}")
    R<ShopBalanceDTO> getBalance(@PathVariable("shopId") Long shopId);

    @GetMapping("/feign/settle/flow/list")
    R<PageResult<BalanceFlowDTO>> listFlows(@RequestParam("shopId") Long shopId,
                                            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize);

    // ========== 结算单 ==========

    @GetMapping("/feign/settle/record/list")
    R<PageResult<SettleRecordDTO>> listSettleRecords(@RequestParam(value = "shopId", required = false) Long shopId,
                                                     @RequestParam(value = "status", required = false) Integer status,
                                                     @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                     @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize);

    // ========== 提现 ==========

    @PostMapping("/feign/settle/withdraw/apply")
    R<String> applyWithdraw(@RequestParam("shopId") Long shopId, @RequestBody WithdrawApplyDTO applyDTO);

    @GetMapping("/feign/settle/withdraw/list")
    R<PageResult<WithdrawRecordDTO>> listWithdraws(@RequestParam(value = "shopId", required = false) Long shopId,
                                                   @RequestParam(value = "status", required = false) Integer status,
                                                   @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                   @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize);

    @PostMapping("/feign/settle/withdraw/audit")
    R<Boolean> auditWithdraw(@RequestParam("withdrawId") Long withdrawId,
                             @RequestParam("pass") Boolean pass,
                             @RequestParam(value = "rejectReason", required = false) String rejectReason,
                             @RequestParam(value = "auditor", required = false) String auditor);

    // ========== 佣金规则 ==========

    @GetMapping("/feign/settle/commission/list")
    R<List<CommissionRuleDTO>> listCommissionRules();

    @PostMapping("/feign/settle/commission/save")
    R<Boolean> saveCommissionRule(@RequestBody CommissionRuleDTO dto);

    @DeleteMapping("/feign/settle/commission/{id}")
    R<Boolean> deleteCommissionRule(@PathVariable("id") Long id);
}
