package com.byw.merchant.controller;

import com.byw.api.settle.SettleFeignClient;
import com.byw.api.settle.dto.BalanceFlowDTO;
import com.byw.api.settle.dto.SettleRecordDTO;
import com.byw.api.settle.dto.ShopBalanceDTO;
import com.byw.api.settle.dto.WithdrawApplyDTO;
import com.byw.api.settle.dto.WithdrawRecordDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequirePerm;
import com.byw.common.security.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 商家端结算与提现：余额/流水/结算单/提现发起/提现记录，作用域强制为 UserContext 的 shopId。
 */
@RestController
@RequestMapping("/merchant/settle")
@RequirePerm("m:settle:manage")
@RequiredArgsConstructor
public class MerchantSettleController {

    private final SettleFeignClient settleFeignClient;

    /** 我的余额账户 */
    @GetMapping("/balance")
    public R<ShopBalanceDTO> balance() {
        return settleFeignClient.getBalance(UserContext.getShopId());
    }

    /** 余额流水 */
    @GetMapping("/flow/list")
    public R<PageResult<BalanceFlowDTO>> flowList(@RequestParam(defaultValue = "1") Integer pageNum,
                                                  @RequestParam(defaultValue = "10") Integer pageSize) {
        return settleFeignClient.listFlows(UserContext.getShopId(), pageNum, pageSize);
    }

    /** 结算单列表 */
    @GetMapping("/record/list")
    public R<PageResult<SettleRecordDTO>> recordList(@RequestParam(required = false) Integer status,
                                                     @RequestParam(defaultValue = "1") Integer pageNum,
                                                     @RequestParam(defaultValue = "10") Integer pageSize) {
        return settleFeignClient.listSettleRecords(UserContext.getShopId(), status, pageNum, pageSize);
    }

    /** 发起提现申请 */
    @PostMapping("/withdraw/apply")
    public R<String> applyWithdraw(@RequestBody WithdrawApplyDTO applyDTO) {
        return settleFeignClient.applyWithdraw(UserContext.getShopId(), applyDTO);
    }

    /** 我的提现记录 */
    @GetMapping("/withdraw/list")
    public R<PageResult<WithdrawRecordDTO>> withdrawList(@RequestParam(required = false) Integer status,
                                                         @RequestParam(defaultValue = "1") Integer pageNum,
                                                         @RequestParam(defaultValue = "10") Integer pageSize) {
        return settleFeignClient.listWithdraws(UserContext.getShopId(), status, pageNum, pageSize);
    }
}
