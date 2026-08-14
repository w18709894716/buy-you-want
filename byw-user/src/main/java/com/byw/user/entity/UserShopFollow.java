package com.byw.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户店铺关注（店铺级关注）
 */
@Data
@TableName("t_user_shop_follow")
public class UserShopFollow {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long shopId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
