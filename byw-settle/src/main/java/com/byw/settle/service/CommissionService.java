package com.byw.settle.service;

import com.byw.api.order.dto.OrderDetailDTO;

import java.math.BigDecimal;

/**
 * 佣金计算服务：按商品分类佣金率逐明细计算平台佣金。
 */
public interface CommissionService {

    /**
     * 计算某子订单的平台佣金总额。
     * 逐明细：itemActual = subtotal × (payAmount / totalAmount)；
     * rate = 该商品分类佣金率（无则用平台默认兜底率）；commission = Σ itemActual × rate。
     */
    BigDecimal calcCommission(OrderDetailDTO order);
}
