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

    /** 查询用户绑定的角色列表（含角色名）。userType：1平台员工 2商家账号 */
    List<SysRoleDTO> listUserRoles(Long userId, Integer userType);

    /** 解绑用户与角色的绑定关系，并清理该用户权限缓存 */
    boolean unbindUserRole(Long userId, Integer userType, Long roleId);

    // ===== 角色 =====
    List<SysRoleDTO> listRoles(String scope);

    /** 商家可见角色：平台预设模板（shop_id=NULL）+ 本店自定义角色（shop_id=shopId） */
    List<SysRoleDTO> listMerchantRoles(Long shopId);

    SysRoleDTO getRole(Long roleId);

    /** 新建角色（dto 需带 scope；商家角色需带 shopId，roleCode 为空则自动生成） */
    Long createRole(SysRoleDTO dto);

    /** 复制角色：基于源角色（含预设模板）创建自定义角色并继承其菜单授权 */
    Long copyRole(Long sourceRoleId, SysRoleDTO dto);

    /** 查询绑定指定角色的用户ID列表（user_type=2 商家账号） */
    List<Long> listRoleUserIds(Long roleId);

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

    // ===== 菜单管理（平台端动态维护两端菜单树） =====

    /** 全量菜单树（不过滤状态，含停用菜单），供菜单管理页渲染 */
    List<SysMenuDTO> getMenuTreeAll(String scope);

    /** 新增菜单（目录/菜单/按钮），返回新菜单ID */
    Long createMenu(SysMenuDTO dto);

    /** 编辑菜单；permCode 变更时清理绑定该菜单角色的用户权限缓存 */
    boolean updateMenu(SysMenuDTO dto);

    /** 删除菜单（存在子菜单或已被角色绑定则拒绝） */
    boolean deleteMenu(Long menuId);
}
