package com.byw.common.rocketmq.constant;

public class RocketMQTopics {

    // Order related
    public static final String ORDER_STATUS_CHANGE = "order-status-change";
    public static final String ORDER_CREATE = "order-create";
    public static final String ORDER_TIMEOUT_CANCEL = "order-timeout-cancel";

    // Payment related
    public static final String PAYMENT_RESULT = "payment-result";

    // Logistics related
    public static final String LOGISTICS_UPDATE = "logistics-update";

    // Product related
    public static final String PRODUCT_SYNC_ES = "product-sync-es";

    // Seckill related
    public static final String SECKILL_ORDER = "seckill-order";

    // User behavior
    public static final String USER_BEHAVIOR = "user-behavior";

    private RocketMQTopics() {}
}
