package com.byw.api.logistics.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ShipRequestDTO implements Serializable {
    private String orderNo;
    private String companyCode;
    private String companyName;
    private String senderName;
    private String senderPhone;
    private String senderAddress;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
}
