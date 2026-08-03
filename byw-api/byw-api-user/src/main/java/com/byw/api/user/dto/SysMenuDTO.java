package com.byw.api.user.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 菜单/权限（t_sys_menu）传输对象，含 children 树形结构，供前端动态菜单渲染。
 */
@Data
public class SysMenuDTO implements Serializable {

    private Long id;
    private Long parentId;
    private String menuName;
    /** 1目录 2菜单 3按钮 */
    private Integer menuType;
    /** platform / merchant */
    private String scope;
    private String path;
    private String permCode;
    private String icon;
    private Integer sortOrder;
    private Integer visible;
    private Integer status;

    /** 子菜单（树形下发） */
    private List<SysMenuDTO> children;
}
