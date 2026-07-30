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
    /** 关闭类型 null未关闭 1取消关闭 2退款关闭 */
    private Integer closeType;
    /** 关联退款类售后单ID（无则为空） */
    private Long afterSaleId;
    /** 退款类售后状态 0待审核 1待买家寄回 2已拒绝 3已完成 4已撤销 5待商家收货 6退款中 */
    private Integer afterSaleStatus;
    /** 退款类售后类型 1仅退款 2退货退款 */
    private Integer afterSaleType;
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
        /** 该商品最新退款类售后单ID（无则为空） */
        private Long afterSaleId;
        /** 该商品退款类售后状态 0待审核 1待买家寄回 2已拒绝 3已完成 4已撤销 5待商家收货 6退款中 */
        private Integer afterSaleStatus;
        /** 该商品退款类售后类型 1仅退款 2退货退款 */
        private Integer afterSaleType;
    }
}
