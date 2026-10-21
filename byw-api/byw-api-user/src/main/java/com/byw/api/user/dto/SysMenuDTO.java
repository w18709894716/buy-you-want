package com.byw.api.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 50, message = "菜单名称过长")
    private String menuName;
    /** 1目录 2菜单 3按钮 */
    @NotNull(message = "请选择菜单类型")
    @Min(value = 1, message = "菜单类型只能是 1目录/2菜单/3按钮")
    @Max(value = 3, message = "菜单类型只能是 1目录/2菜单/3按钮")
    private Integer menuType;
    /** platform / merchant（由调用方决定，只做格式校验、不设必填） */
    @Pattern(regexp = "platform|merchant", message = "菜单适用端只能是 platform 或 merchant")
    private String scope;
    @Size(max = 200, message = "路由路径过长")
    private String path;
    @Size(max = 100, message = "权限标识过长")
    private String permCode;
    @Size(max = 50, message = "图标名过长")
    private String icon;
    private Integer sortOrder;
    private Integer visible;
    private Integer status;

    /** 子菜单（树形下发） */
    private List<SysMenuDTO> children;
}
