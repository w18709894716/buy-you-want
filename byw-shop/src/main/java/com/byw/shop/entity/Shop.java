package com.byw.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_shop")
public class Shop {

    @TableId(type = IdType.AUTO)
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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
