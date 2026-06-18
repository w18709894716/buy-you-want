package com.byw.api.pay.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class PayOrderDTO implements Serializable {
    private Long id;
    private String payNo;
    private String orderNo;
    private Long userId;
    private BigDecimal amount;
    private String payChannel;
    private Integer status;
    private String payUrl;
}
