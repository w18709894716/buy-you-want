package com.byw.order.service.compensation;

/**
 * 后置补偿动作的类型。新增类型时同时要：
 * 1. 在 {@code CompensationPayloads} 里加参数快照；
 * 2. 加一个 {@link CompensationHandler} 实现（Spring 自动按 {@link CompensationHandler#type()} 注册）。
 *
 * <p>注意：类型的执行体必须是**幂等**的，否则重试会放大错误
 * （典型反例：库存回补 {@code stock = stock + q} 无守卫，重复执行会虚增库存，故未纳入）。
 */
public enum CompensationType {

    /** 取消已支付订单后的退款，幂等键 CANCEL:{orderNo}（与 pay 侧一致） */
    CANCEL_REFUND,

    /** 售后退款后冲减商家结算应得，幂等键 afterSaleNo（与结算侧 refNo 一致） */
    SETTLE_ADJUST,

    /** 下单失败撤回优惠券核销，幂等键 {parentOrderNo}:{couponId} */
    RELEASE_COUPON,

    /** 下单失败撤回库存预占，幂等键 {parentOrderNo} */
    RELEASE_STOCK
}
