package com.byw.order.service.compensation.handler;

import com.byw.api.settle.SettleFeignClient;
import com.byw.common.core.result.R;
import com.byw.order.entity.CompensationTask;
import com.byw.order.service.compensation.CompensationHandler;
import com.byw.order.service.compensation.CompensationPayloads;
import com.byw.order.service.compensation.CompensationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 售后退款后冲减商家应得份额。
 *
 * <p>不冲减的后果是"同一笔钱付两次"：买家拿到了退款，结算冷静期一到仍把全额打进商家可用余额。
 *
 * <p>幂等由售后单号保证（结算侧按 refNo 查 t_balance_flow 是否已有冲减流水）。
 * 结算侧"余额不足（可能已提现）"这类错误重试也不会好转，但接口只返回 R.fail(message)、
 * 没有错误码，靠关键字匹配分类不可靠 —— 所以统一重试到上限后转需人工，由人决定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettleAdjustHandler implements CompensationHandler {

    private final SettleFeignClient settleFeignClient;
    private final ObjectMapper objectMapper;

    @Override
    public CompensationType type() {
        return CompensationType.SETTLE_ADJUST;
    }

    @Override
    public void execute(CompensationTask task) throws Exception {
        CompensationPayloads.SettleAdjust payload =
                objectMapper.readValue(task.getPayload(), CompensationPayloads.SettleAdjust.class);
        BigDecimal amount = payload.amount();
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("冲减金额非法: " + amount);
        }
        if (task.getAfterSaleNo() == null || task.getAfterSaleNo().isBlank()) {
            throw new IllegalArgumentException("缺少售后单号，无法幂等冲减");
        }
        R<Boolean> result = settleFeignClient.adjustOnRefund(
                task.getOrderNo(), amount, task.getAfterSaleNo());
        if (result == null || !result.isSuccess()) {
            throw new IllegalStateException("结算冲减未成功: "
                    + (result == null ? "响应为空" : result.getCode() + "/" + result.getMessage()));
        }
        log.info("退款结算冲减完成: afterSaleNo={}, orderNo={}, amount={}",
                task.getAfterSaleNo(), task.getOrderNo(), amount);
    }
}
