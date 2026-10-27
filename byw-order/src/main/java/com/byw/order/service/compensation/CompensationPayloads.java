package com.byw.order.service.compensation;

import java.math.BigDecimal;
import java.util.List;

/**
 * 各类补偿任务的执行参数快照。
 *
 * <p>为什么快照而不是执行时回查业务表：执行可能发生在几分钟甚至几小时后，届时订单/售后单
 * 的状态、金额都可能已经变了；补偿要还原的是"当时决定要做的那件事"，不是一个新决定。
 * 订单号/售后单号另存在任务表的独立列里，便于按单检索，所以这里不重复放。
 */
public final class CompensationPayloads {

    /** 取消订单退款：金额取下单实付，固定原因"订单取消退款" */
    public record CancelRefund(BigDecimal amount) {
    }

    /** 售后退款冲减结算：金额取售后单的 refund_amount */
    public record SettleAdjust(BigDecimal amount) {
    }

    /** 撤回优惠券核销 */
    public record ReleaseCoupon(Long couponId, Long userId) {
    }

    /** 撤回库存预占 */
    public record ReleaseStock(List<Item> items) {

        public record Item(Long skuId, Integer quantity) {
        }
    }

    private CompensationPayloads() {
    }
}
