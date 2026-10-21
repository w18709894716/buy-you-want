package com.byw.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_address")
public class UserAddress {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 收货人：对应 VARCHAR(50) NOT NULL */
    @NotBlank(message = "请填写收货人")
    @Size(max = 50, message = "收货人姓名过长")
    private String receiverName;
    /** 联系电话：对应 VARCHAR(20) NOT NULL */
    @NotBlank(message = "请填写联系电话")
    @Size(max = 20, message = "联系电话过长")
    private String receiverPhone;
    /** 省份（数据库可空，未强制必填） */
    @Size(max = 50, message = "省份名称过长")
    private String province;
    @Size(max = 50, message = "城市名称过长")
    private String city;
    @Size(max = 50, message = "区县名称过长")
    private String district;
    /** 详细地址：对应 VARCHAR(200) NOT NULL */
    @NotBlank(message = "请填写详细地址")
    @Size(max = 200, message = "详细地址过长")
    private String detailAddress;
    private Integer isDefault;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
