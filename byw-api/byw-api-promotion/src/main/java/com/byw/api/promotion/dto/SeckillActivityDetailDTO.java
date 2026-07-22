package com.byw.api.promotion.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SeckillActivityDetailDTO implements Serializable {

    private Long id;
    private String name;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;

    /** 活动商品条目（含商品信息，由 byw-product 填充） */
    private List<SeckillItemDetailDTO> items;

    @Data
    public static class SeckillItemDetailDTO implements Serializable {
        private Long itemId;
        private Long productId;
        private Long skuId;
        private BigDecimal seckillPrice;
        private Integer totalStock;
        private Integer availableStock;

        // 商品信息（由 byw-product 填充）
        private String productName;
        private String productImage;
        private String skuName;
        private BigDecimal originalPrice;
    }
}
