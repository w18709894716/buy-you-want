package com.byw.order.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 退款明细（C端查看退款流程与到账信息）
 */
@Data
public class RefundDetailDTO implements Serializable {

    /** 售后单ID */
    private Long afterSaleId;
    /** 售后单号 */
    private String afterSaleNo;
    /** 关联订单号 */
    private String orderNo;
    /** 售后商品名称快照（商品级售后有值） */
    private String productName;
    /** 售后SKU规格快照（商品级售后有值） */
    private String skuName;
    /** 售后类型 1仅退款 2退货退款 */
    private Integer type;
    /** 售后状态 0待审核 1待买家寄回 2已拒绝 3已完成 4已撤销 5待商家收货 6退款中 */
    private Integer status;

    /** 申请退款金额 */
    private BigDecimal refundAmount;
    /** 拒绝原因（若被拒） */
    private String rejectReason;

    /** 退款单号（退款发起后有值） */
    private String refundNo;
    /** 退款状态 0处理中 1成功 2失败 */
    private Integer refundStatus;
    /** 原路退回渠道 */
    private String payChannel;
    /** 退款发起时间 */
    private LocalDateTime refundCreatedAt;
    /** 退款到账/更新时间 */
    private LocalDateTime refundUpdatedAt;

    /** 退款流程时间线节点 */
    private List<TimelineNode> timeline;

    /**
     * 时间线节点
     */
    @Data
    public static class TimelineNode implements Serializable {
        /** 节点标题 */
        private String title;
        /** 节点时间（未到达则为空） */
        private LocalDateTime time;
        /** 是否已到达/点亮 */
        private Boolean reached;

        public TimelineNode() {
        }

        public TimelineNode(String title, LocalDateTime time, Boolean reached) {
            this.title = title;
            this.time = time;
            this.reached = reached;
        }
    }
}
