package com.byw.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜单/权限（平台与商家共用，scope 区分）。
 */
@Data
@TableName("t_sys_menu")
public class SysMenu {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String menuName;
    private Integer menuType;
    private String scope;
    private String path;
    private String permCode;
    private String icon;
    private Integer sortOrder;
    private Integer visible;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
