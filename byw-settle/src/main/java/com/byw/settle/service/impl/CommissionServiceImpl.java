package com.byw.settle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.ProductDTO;
import com.byw.common.core.result.R;
import com.byw.settle.entity.CommissionRule;
import com.byw.settle.mapper.CommissionRuleMapper;
import com.byw.settle.service.CommissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionServiceImpl implements CommissionService {

    private final CommissionRuleMapper commissionRuleMapper;
    private final ProductFeignClient productFeignClient;

    /** 平台默认兜底佣金率（佣金规则表缺失时使用） */
    private static final BigDecimal FALLBACK_RATE = new BigDecimal("0.0500");

    @Override
    public BigDecimal calcCommission(OrderDetailDTO order) {
        List<OrderDetailDTO.OrderItemDTO> items = order.getItems();
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalAmount = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
        BigDecimal payAmount = order.getPayAmount() == null ? BigDecimal.ZERO : order.getPayAmount();
        // 实付/订单总额比例（用于把明细金额折算到实付基数；总额为0时按1处理）
        BigDecimal payRatio = totalAmount.compareTo(BigDecimal.ZERO) > 0
                ? payAmount.divide(totalAmount, 6, RoundingMode.HALF_UP)
                : BigDecimal.ONE;

        BigDecimal defaultRate = queryRate(0L);
        Map<Long, BigDecimal> rateCache = new HashMap<>();

        BigDecimal commission = BigDecimal.ZERO;
        for (OrderDetailDTO.OrderItemDTO item : items) {
            BigDecimal subtotal = item.getSubtotal() == null ? BigDecimal.ZERO : item.getSubtotal();
            BigDecimal itemActual = subtotal.multiply(payRatio);

            Long categoryId = resolveCategoryId(item.getProductId());
            BigDecimal rate = categoryId == null ? defaultRate
                    : rateCache.computeIfAbsent(categoryId, cid -> {
                        BigDecimal r = queryRate(cid);
                        return r != null ? r : defaultRate;
                    });
            if (rate == null) {
                rate = FALLBACK_RATE;
            }
            commission = commission.add(itemActual.multiply(rate));
        }
        return commission.setScale(2, RoundingMode.HALF_UP);
    }

    /** 查询指定分类的启用佣金率；不存在返回 null（categoryId=0 为平台默认） */
    private BigDecimal queryRate(Long categoryId) {
        CommissionRule rule = commissionRuleMapper.selectOne(new LambdaQueryWrapper<CommissionRule>()
                .eq(CommissionRule::getCategoryId, categoryId)
                .eq(CommissionRule::getEnabled, 1)
                .last("LIMIT 1"));
        return rule == null ? null : rule.getCommissionRate();
    }

    /** 通过商品服务获取商品分类ID；失败返回 null（后续走默认率） */
    private Long resolveCategoryId(Long productId) {
        if (productId == null) {
            return null;
        }
        try {
            R<ProductDTO> resp = productFeignClient.getProductById(productId);
            if (resp != null && resp.isSuccess() && resp.getData() != null) {
                return resp.getData().getCategoryId();
            }
        } catch (Exception e) {
            log.warn("获取商品分类失败, productId={}, 使用默认佣金率", productId, e);
        }
        return null;
    }
}
