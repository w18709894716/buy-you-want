package com.byw.api.promotion.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CouponDTO implements Serializable {
    private Long id;
    private String name;
    private Integer type;
    private BigDecimal discountValue;
    private BigDecimal minAmount;
    private Integer totalCount;
    private Integer claimedCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    /** 是否新人专享：0普通 1新人专享 */
    private Integer newUser;
    /** 归属店铺ID：null或0平台券(全场通用) 其他为店铺券 */
    private Long shopId;
}
