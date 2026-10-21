package com.byw.api.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderCreateDTO implements Serializable {
    private Long userId;
    /** 收货地址ID（必填） */
    @NotNull(message = "请选择收货地址")
    private Long addressId;
    private String remark;
    private Long couponId;
    /** 下单商品明细（必填，至少一条） */
    @NotEmpty(message = "下单商品不能为空")
    @Valid
    private List<OrderItemDTO> items;

    @Data
    public static class OrderItemDTO implements Serializable {
        private Long productId;
        /** 商品规格ID（必填） */
        @NotNull(message = "商品规格不能为空")
        private Long skuId;
        /** 归属店铺ID（下单时按此字段拆分子订单；前端可不传，后端会从商品服务回填） */
        private Long shopId;
        private String productName;
        private String skuName;
        private String productImage;
        private BigDecimal price;
        /** 购买数量：与 OrderServiceImpl.MAX_ITEM_QUANTITY 保持一致 */
        @NotNull(message = "购买数量不能为空")
        @Min(value = 1, message = "购买数量必须大于 0")
        @Max(value = 999, message = "单个商品最多购买 999 件")
        private Integer quantity;
    }
}
