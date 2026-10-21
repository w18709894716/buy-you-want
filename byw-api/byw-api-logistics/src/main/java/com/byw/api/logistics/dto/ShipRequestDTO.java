package com.byw.api.logistics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class ShipRequestDTO implements Serializable {
    @NotBlank(message = "订单号不能为空")
    @Size(max = 64, message = "订单号过长")
    private String orderNo;
    @Size(max = 50, message = "物流公司编码过长")
    private String companyCode;
    @Size(max = 100, message = "物流公司名称过长")
    private String companyName;
    /** 运单号：为空时由物流服务自动生成 */
    @Size(max = 100, message = "运单号过长")
    private String trackingNo;
    @Size(max = 50, message = "寄件人姓名过长")
    private String senderName;
    @Size(max = 20, message = "寄件人电话过长")
    private String senderPhone;
    @Size(max = 300, message = "寄件人地址过长")
    private String senderAddress;
    @Size(max = 50, message = "收件人姓名过长")
    private String receiverName;
    @Size(max = 20, message = "收件人电话过长")
    private String receiverPhone;
    @Size(max = 300, message = "收件人地址过长")
    private String receiverAddress;
}
