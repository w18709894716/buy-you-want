package com.byw.api.shop.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 店铺信息（平台/商家/用户端共享契约）。
 */
@Data
public class ShopDTO implements Serializable {

    private Long id;

    /** 店铺名称 */
    private String name;

    /** 店铺Logo */
    private String logo;

    /** 店铺简介 */
    private String description;

    /** 归属商家账号ID */
    private Long merchantId;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 0自营 1第三方商家 */
    private Integer selfOperated;

    /** 店铺状态 0关店 1营业 2封禁 */
    private Integer status;

    private LocalDateTime createdAt;
}
