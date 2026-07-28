package com.byw.api.order.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderDetailDTO implements Serializable {
    private Long id;
    private String orderNo;
    /** 父订单号（子订单指向父订单；父订单本身为空） */
    private String parentOrderNo;
    /** 是否父订单：0子/普通订单 1父订单 */
    private Integer isParent;
    private Long userId;
    private Long shopId;
    /** 归属店铺名称（拆单后子订单展示用） */
    private String shopName;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private BigDecimal freightAmount;
    private BigDecimal discountAmount;
    private Integer status;
    /** 评价状态：0未评价 1已评价 */
    private Integer reviewed;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String remark;
    private LocalDateTime payTime;
    private LocalDateTime shipTime;
    private LocalDateTime receiveTime;
    private LocalDateTime createdAt;
    private List<OrderItemDTO> items;

    @Data
    public static class OrderItemDTO implements Serializable {
        private Long id;
        private Long productId;
        private Long shopId;
        private Long skuId;
        private String productName;
        private String skuName;
        private String productImage;
        private BigDecimal price;
        private Integer quantity;
        private BigDecimal subtotal;
        /** 发货状态 0未发货 1已发货 */
        private Integer shipStatus;
        private String trackingNo;
        private String companyName;
        private LocalDateTime shipTime;
    }
}
