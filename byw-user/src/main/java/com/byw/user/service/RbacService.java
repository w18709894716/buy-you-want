package com.byw.user.service;

import com.byw.api.user.dto.SysMenuDTO;
import com.byw.api.user.dto.SysRoleDTO;
import com.byw.api.user.dto.SysUserDTO;
import com.byw.common.core.result.PageResult;

import java.util.List;

/**
 * RBAC 数据服务：平台员工、角色、菜单权限的持有与聚合。
 */
public interface RbacService {

    // ===== 平台员工 =====
    SysUserDTO getSysUserByUsername(String username);

    PageResult<SysUserDTO> getSysUserPage(Integer pageNum, Integer pageSize, String keyword);

    Long createSysUser(SysUserDTO dto);

    boolean updateSysUser(SysUserDTO dto);

    boolean updateSysUserStatus(Long userId, Integer status);

    boolean resetSysUserPassword(Long userId, String password);

    /** 覆盖式分配角色。userType：1平台员工 2商家账号 */
    boolean assignRoles(Long userId, Integer userType, List<Long> roleIds);

    // ===== 角色 =====
    List<SysRoleDTO> listRoles(String scope);

    /** 商家可见角色：平台预设模板（shop_id=NULL）+ 本店自定义角色（shop_id=shopId） */
    List<SysRoleDTO> listMerchantRoles(Long shopId);

    SysRoleDTO getRole(Long roleId);

    /** 新建角色（dto 需带 scope；商家角色需带 shopId，roleCode 为空则自动生成） */
    Long createRole(SysRoleDTO dto);

    /** 编辑角色（仅名称/备注；内置预设不可改） */
    boolean updateRole(SysRoleDTO dto);

    /** 删除角色（软删；内置预设/超级管理员不可删；仍有成员绑定则拒绝） */
    boolean deleteRole(Long roleId);

    List<Long> getRoleMenuIds(Long roleId);

    boolean bindRoleMenus(Long roleId, List<Long> menuIds);

    // ===== 权限 / 菜单 =====
    List<String> listPermCodes(Integer userType, Long userId);

    List<SysMenuDTO> getMenuTree(String scope, Integer userType, Long userId);

    List<SysMenuDTO> getAllMenuTree(String scope);
}
