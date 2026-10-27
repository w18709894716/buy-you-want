package com.byw.order.service.compensation.handler;

import com.byw.api.pay.PayFeignClient;
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
 * 取消已支付订单后的退款。
 *
 * <p>幂等由 pay 侧的 refNo {@code CANCEL:{orderNo}} 保证：同一订单重复调用只会退一次，
 * 所以任务被重试、被重放都不会多退钱。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CancelRefundHandler implements CompensationHandler {

    /** 固定文案，不拼用户填写的取消原因（t_refund_record.reason 是 VARCHAR(200)，超长会被截断报错） */
    private static final String REFUND_REASON = "订单取消退款";

    private final PayFeignClient payFeignClient;
    private final ObjectMapper objectMapper;

    @Override
    public CompensationType type() {
        return CompensationType.CANCEL_REFUND;
    }

    @Override
    public void execute(CompensationTask task) throws Exception {
        CompensationPayloads.CancelRefund payload =
                objectMapper.readValue(task.getPayload(), CompensationPayloads.CancelRefund.class);
        BigDecimal amount = payload.amount();
        if (amount == null || amount.signum() <= 0) {
            // 参数非法说明登记时就错了，重试多少次都不会变好，直接失败让调用方转需人工
            throw new IllegalArgumentException("退款金额非法: " + amount);
        }
        String refNo = "CANCEL:" + task.getOrderNo();
        R<Boolean> result = payFeignClient.refund(task.getOrderNo(), amount, REFUND_REASON, refNo);
        if (result == null || !result.isSuccess() || !Boolean.TRUE.equals(result.getData())) {
            throw new IllegalStateException("退款未成功: "
                    + (result == null ? "响应为空" : result.getCode() + "/" + result.getMessage()));
        }
        log.info("取消订单退款完成: orderNo={}, amount={}, refNo={}", task.getOrderNo(), amount, refNo);
    }
}
