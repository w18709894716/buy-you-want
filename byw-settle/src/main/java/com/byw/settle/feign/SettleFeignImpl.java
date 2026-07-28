package com.byw.settle.feign;

import com.byw.api.settle.SettleFeignClient;
import com.byw.api.settle.dto.BalanceFlowDTO;
import com.byw.api.settle.dto.CommissionRuleDTO;
import com.byw.api.settle.dto.SettleRecordDTO;
import com.byw.api.settle.dto.ShopBalanceDTO;
import com.byw.api.settle.dto.WithdrawApplyDTO;
import com.byw.api.settle.dto.WithdrawRecordDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.Public;
import com.byw.settle.service.BalanceService;
import com.byw.settle.service.CommissionRuleService;
import com.byw.settle.service.SettleService;
import com.byw.settle.service.WithdrawService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 结算服务契约实现（供 byw-merchant / byw-admin BFF 通过 Feign 调用）。
 */
@RestController
@RequestMapping("/feign/settle")
@RequiredArgsConstructor
@Public
public class SettleFeignImpl implements SettleFeignClient {

    private final BalanceService balanceService;
    private final SettleService settleService;
    private final WithdrawService withdrawService;
    private final CommissionRuleService commissionRuleService;

    @Override
    @GetMapping("/balance/{shopId}")
    public R<ShopBalanceDTO> getBalance(@PathVariable("shopId") Long shopId) {
        return R.ok(balanceService.getBalanceDTO(shopId));
    }

    @Override
    @GetMapping("/flow/list")
    public R<PageResult<BalanceFlowDTO>> listFlows(@RequestParam("shopId") Long shopId,
                                                   @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                   @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return R.ok(balanceService.listFlows(shopId, pageNum, pageSize));
    }

    @Override
    @GetMapping("/record/list")
    public R<PageResult<SettleRecordDTO>> listSettleRecords(@RequestParam(value = "shopId", required = false) Long shopId,
                                                            @RequestParam(value = "status", required = false) Integer status,
                                                            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return R.ok(settleService.listSettleRecords(shopId, status, pageNum, pageSize));
    }

    @Override
    @PostMapping("/withdraw/apply")
    public R<String> applyWithdraw(@RequestParam("shopId") Long shopId, @RequestBody WithdrawApplyDTO applyDTO) {
        return R.ok(withdrawService.applyWithdraw(shopId, applyDTO));
    }

    @Override
    @GetMapping("/withdraw/list")
    public R<PageResult<WithdrawRecordDTO>> listWithdraws(@RequestParam(value = "shopId", required = false) Long shopId,
                                                          @RequestParam(value = "status", required = false) Integer status,
                                                          @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                          @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return R.ok(withdrawService.listWithdraws(shopId, status, pageNum, pageSize));
    }

    @Override
    @PostMapping("/withdraw/audit")
    public R<Boolean> auditWithdraw(@RequestParam("withdrawId") Long withdrawId,
                                    @RequestParam("pass") Boolean pass,
                                    @RequestParam(value = "rejectReason", required = false) String rejectReason,
                                    @RequestParam(value = "auditor", required = false) String auditor) {
        return R.ok(withdrawService.auditWithdraw(withdrawId, pass, rejectReason, auditor));
    }

    @Override
    @GetMapping("/commission/list")
    public R<List<CommissionRuleDTO>> listCommissionRules() {
        return R.ok(commissionRuleService.listRules());
    }

    @Override
    @PostMapping("/commission/save")
    public R<Boolean> saveCommissionRule(@RequestBody CommissionRuleDTO dto) {
        return R.ok(commissionRuleService.saveRule(dto));
    }

    @Override
    @DeleteMapping("/commission/{id}")
    public R<Boolean> deleteCommissionRule(@PathVariable("id") Long id) {
        return R.ok(commissionRuleService.deleteRule(id));
    }
}
