package com.byw.api.order.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderCreateDTO implements Serializable {
    private Long userId;
    private Long addressId;
    private String remark;
    private Long couponId;
    private List<OrderItemDTO> items;

    @Data
    public static class OrderItemDTO implements Serializable {
        private Long productId;
        private Long skuId;
        /** 归属店铺ID（下单时按此字段拆分子订单；前端可不传，后端会从商品服务回填） */
        private Long shopId;
        private String productName;
        private String skuName;
        private String productImage;
        private BigDecimal price;
        private Integer quantity;
    }
}
