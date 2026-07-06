package com.byw.api.promotion.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserCouponDTO implements Serializable {
    /** 领取记录ID */
    private Long recordId;
    /** 优惠券模板ID */
    private Long couponId;
    /** 优惠券名称 */
    private String name;
    /** 1满减券 2折扣券 3无门槛券 */
    private Integer type;
    /** 优惠金额/折扣比例 */
    private BigDecimal discountValue;
    /** 最低消费门槛 */
    private BigDecimal minAmount;
    /** 有效期开始 */
    private LocalDateTime startTime;
    /** 有效期结束 */
    private LocalDateTime endTime;
}
