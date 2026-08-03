package com.byw.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 角色-菜单关联。
 */
@Data
@TableName("t_sys_role_menu")
public class SysRoleMenu {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roleId;
    private Long menuId;
}
