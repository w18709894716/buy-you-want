package com.byw.order.service.compensation.handler;

import com.byw.api.promotion.PromotionFeignClient;
import com.byw.common.core.result.R;
import com.byw.order.entity.CompensationTask;
import com.byw.order.service.compensation.CompensationHandler;
import com.byw.order.service.compensation.CompensationPayloads;
import com.byw.order.service.compensation.CompensationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 下单失败后撤回优惠券核销。
 *
 * <p>幂等：券服务只把 {@code status=1}（已使用）的记录翻回 0，第二次调用找不到可翻转的行，
 * 是 no-op。所以重试、重放都不会把用户另一笔订单已用掉的券误恢复。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReleaseCouponHandler implements CompensationHandler {

    private final PromotionFeignClient promotionFeignClient;
    private final ObjectMapper objectMapper;

    @Override
    public CompensationType type() {
        return CompensationType.RELEASE_COUPON;
    }

    @Override
    public void execute(CompensationTask task) throws Exception {
        CompensationPayloads.ReleaseCoupon payload =
                objectMapper.readValue(task.getPayload(), CompensationPayloads.ReleaseCoupon.class);
        if (payload.couponId() == null || payload.userId() == null) {
            throw new IllegalArgumentException("缺少 couponId/userId，无法撤回券核销");
        }
        R<Boolean> result = promotionFeignClient.releaseCoupon(payload.couponId(), payload.userId());
        if (result == null || !result.isSuccess()) {
            throw new IllegalStateException("撤回优惠券失败: "
                    + (result == null ? "响应为空" : result.getCode() + "/" + result.getMessage()));
        }
        log.info("下单失败已撤回优惠券核销: couponId={}, userId={}, orderNo={}",
                payload.couponId(), payload.userId(), task.getOrderNo());
    }
}
