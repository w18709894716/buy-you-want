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
}
