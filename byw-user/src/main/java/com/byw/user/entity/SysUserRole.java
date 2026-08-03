package com.byw.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-角色关联（user_type 兼容平台员工与商家账号）。
 */
@Data
@TableName("t_sys_user_role")
public class SysUserRole {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 1平台员工 2商家账号 */
    private Integer userType;
    private Long userId;
    private Long roleId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
