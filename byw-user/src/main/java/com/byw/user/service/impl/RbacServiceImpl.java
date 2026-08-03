package com.byw.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.user.dto.SysMenuDTO;
import com.byw.api.user.dto.SysRoleDTO;
import com.byw.api.user.dto.SysUserDTO;
import com.byw.common.core.constant.CommonConstants;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.result.PageResult;
import com.byw.common.redis.util.RedisUtil;
import com.byw.user.entity.SysMenu;
import com.byw.user.entity.SysRole;
import com.byw.user.entity.SysRoleMenu;
import com.byw.user.entity.SysUser;
import com.byw.user.entity.SysUserRole;
import com.byw.user.mapper.SysMenuMapper;
import com.byw.user.mapper.SysRoleMapper;
import com.byw.user.mapper.SysRoleMenuMapper;
import com.byw.user.mapper.SysUserMapper;
import com.byw.user.mapper.SysUserRoleMapper;
import com.byw.user.service.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RBAC 数据服务实现。超级管理员（role_code=super_admin）拥有全部权限（返回 ["*"]），
 * 其余按 用户-角色 → 角色-菜单 → 菜单.perm_code 去重聚合。
 * 授权调整后删除对应用户的 Redis 权限缓存 key，实现即时生效。
 */
@Service
@RequiredArgsConstructor
public class RbacServiceImpl implements RbacService {

    /** 平台超级管理员角色标识，拥有全部权限 */
    private static final String ROLE_SUPER_ADMIN = "super_admin";
    /** 角色 scope：商家 */
    private static final String SCOPE_MERCHANT = "merchant";
    /** user_type：平台员工 */
    private static final int USER_TYPE_SYS = 1;
    /** user_type：商家账号 */
    private static final int USER_TYPE_MERCHANT = 2;

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final RedisUtil redisUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ===================== 平台员工 =====================

    @Override
    public SysUserDTO getSysUserByUsername(String username) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (user == null) {
            return null;
        }
        SysUserDTO dto = new SysUserDTO();
        BeanUtils.copyProperties(user, dto);
        return dto;
    }

    @Override
    public PageResult<SysUserDTO> getSysUserPage(Integer pageNum, Integer pageSize, String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getNickname, keyword)
                    .or().like(SysUser::getPhone, keyword));
        }
        wrapper.orderByDesc(SysUser::getCreatedAt);
        IPage<SysUser> page = sysUserMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        List<SysUserDTO> dtoList = page.getRecords().stream().map(u -> {
            SysUserDTO dto = new SysUserDTO();
            BeanUtils.copyProperties(u, dto);
            dto.setPassword(null);
            fillRoles(dto, USER_TYPE_SYS);
            return dto;
        }).collect(Collectors.toList());
        return PageResult.of(dtoList, page.getTotal(), pageNum, pageSize);
    }

    /** 回填员工的角色ID与角色名称 */
    private void fillRoles(SysUserDTO dto, int userType) {
        List<Long> roleIds = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserType, userType)
                        .eq(SysUserRole::getUserId, dto.getId()))
                .stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        dto.setRoleIds(roleIds);
        if (!roleIds.isEmpty()) {
            String names = sysRoleMapper.selectBatchIds(roleIds).stream()
                    .map(SysRole::getRoleName).collect(Collectors.joining("、"));
            dto.setRoleNames(names);
        }
    }

    @Override
    public Long createSysUser(SysUserDTO dto) {
        Long exists = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (exists != null && exists > 0) {
            throw new BusinessException("用户名已存在");
        }
        SysUser user = new SysUser();
        BeanUtils.copyProperties(dto, user);
        user.setId(null);
        String rawPwd = (dto.getPassword() == null || dto.getPassword().isEmpty()) ? "123456" : dto.getPassword();
        user.setPassword(passwordEncoder.encode(rawPwd));
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        sysUserMapper.insert(user);
        if (dto.getRoleIds() != null && !dto.getRoleIds().isEmpty()) {
            assignRoles(user.getId(), USER_TYPE_SYS, dto.getRoleIds());
        }
        return user.getId();
    }

    @Override
    public boolean updateSysUser(SysUserDTO dto) {
        SysUser user = sysUserMapper.selectById(dto.getId());
        if (user == null) {
            throw new BusinessException("员工不存在");
        }
        user.setNickname(dto.getNickname());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setAvatar(dto.getAvatar());
        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus());
        }
        sysUserMapper.updateById(user);
        if (dto.getRoleIds() != null) {
            assignRoles(user.getId(), USER_TYPE_SYS, dto.getRoleIds());
        }
        return true;
    }

    @Override
    public boolean updateSysUserStatus(Long userId, Integer status) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setStatus(status);
        return sysUserMapper.updateById(user) > 0;
    }

    @Override
    public boolean resetSysUserPassword(Long userId, String password) {
        SysUser user = new SysUser();
        user.setId(userId);
        String rawPwd = (password == null || password.isEmpty()) ? "123456" : password;
        user.setPassword(passwordEncoder.encode(rawPwd));
        return sysUserMapper.updateById(user) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean assignRoles(Long userId, Integer userType, List<Long> roleIds) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserType, userType)
                .eq(SysUserRole::getUserId, userId));
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                SysUserRole ur = new SysUserRole();
                ur.setUserType(userType);
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                sysUserRoleMapper.insert(ur);
            }
        }
        evictPerms(userType, userId);
        return true;
    }

    // ===================== 角色 =====================

    @Override
    public List<SysRoleDTO> listRoles(String scope) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getScope, scope)
                .eq(SysRole::getStatus, 1)
                .orderByAsc(SysRole::getId);
        return sysRoleMapper.selectList(wrapper).stream().map(r -> {
            SysRoleDTO dto = new SysRoleDTO();
            BeanUtils.copyProperties(r, dto);
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public List<SysRoleDTO> listMerchantRoles(Long shopId) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getScope, SCOPE_MERCHANT)
                .eq(SysRole::getStatus, 1)
                .and(q -> q.isNull(SysRole::getShopId).or().eq(SysRole::getShopId, shopId))
                .orderByAsc(SysRole::getId);
        return sysRoleMapper.selectList(wrapper).stream().map(r -> {
            SysRoleDTO dto = new SysRoleDTO();
            BeanUtils.copyProperties(r, dto);
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public SysRoleDTO getRole(Long roleId) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            return null;
        }
        SysRoleDTO dto = new SysRoleDTO();
        BeanUtils.copyProperties(role, dto);
        return dto;
    }

    @Override
    public Long createRole(SysRoleDTO dto) {
        if (dto.getRoleName() == null || dto.getRoleName().trim().isEmpty()) {
            throw new BusinessException("角色名称不能为空");
        }
        String code = dto.getRoleCode();
        if (code == null || code.trim().isEmpty()) {
            // 商家自定义角色无需手填标识，自动生成唯一 code
            code = (SCOPE_MERCHANT.equals(dto.getScope()) ? "m_shop" + dto.getShopId() + "_" : "role_") + System.currentTimeMillis();
        }
        Long exists = sysRoleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, code));
        if (exists != null && exists > 0) {
            throw new BusinessException("角色标识已存在");
        }
        SysRole role = new SysRole();
        role.setRoleCode(code);
        role.setRoleName(dto.getRoleName().trim());
        role.setScope(dto.getScope());
        role.setShopId(dto.getShopId());
        role.setIsPreset(0);
        role.setRemark(dto.getRemark());
        role.setStatus(1);
        sysRoleMapper.insert(role);
        return role.getId();
    }

    @Override
    public boolean updateRole(SysRoleDTO dto) {
        SysRole role = sysRoleMapper.selectById(dto.getId());
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        if (role.getIsPreset() != null && role.getIsPreset() == 1) {
            throw new BusinessException("内置预设角色不可修改");
        }
        if (dto.getRoleName() != null && !dto.getRoleName().trim().isEmpty()) {
            role.setRoleName(dto.getRoleName().trim());
        }
        role.setRemark(dto.getRemark());
        sysRoleMapper.updateById(role);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteRole(Long roleId) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        if (role.getIsPreset() != null && role.getIsPreset() == 1) {
            throw new BusinessException("内置预设角色不可删除");
        }
        if (ROLE_SUPER_ADMIN.equals(role.getRoleCode())) {
            throw new BusinessException("超级管理员不可删除");
        }
        Long bound = sysUserRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, roleId));
        if (bound != null && bound > 0) {
            throw new BusinessException("该角色下仍有成员，请先解绑后再删除");
        }
        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, roleId));
        sysRoleMapper.deleteById(roleId);
        return true;
    }

    @Override
    public List<Long> getRoleMenuIds(Long roleId) {
        return sysRoleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getRoleId, roleId))
                .stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean bindRoleMenus(Long roleId, List<Long> menuIds) {
        sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds != null) {
            for (Long menuId : menuIds) {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(menuId);
                sysRoleMenuMapper.insert(rm);
            }
        }
        // 删除绑定该角色的所有用户的权限缓存，实现即时生效
        List<SysUserRole> holders = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, roleId));
        for (SysUserRole ur : holders) {
            evictPerms(ur.getUserType(), ur.getUserId());
        }
        return true;
    }

    // ===================== 权限 / 菜单 =====================

    @Override
    public List<String> listPermCodes(Integer userType, Long userId) {
        List<Long> roleIds = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserType, userType)
                        .eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<SysRole> roles = sysRoleMapper.selectBatchIds(roleIds);
        boolean superAdmin = roles.stream().anyMatch(r -> ROLE_SUPER_ADMIN.equals(r.getRoleCode()));
        if (superAdmin) {
            return Collections.singletonList(CommonConstants.PERM_ALL);
        }
        List<Long> menuIds = sysRoleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .in(SysRoleMenu::getRoleId, roleIds))
                .stream().map(SysRoleMenu::getMenuId).distinct().collect(Collectors.toList());
        if (menuIds.isEmpty()) {
            return Collections.emptyList();
        }
        return sysMenuMapper.selectBatchIds(menuIds).stream()
                .map(SysMenu::getPermCode)
                .filter(p -> p != null && !p.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<SysMenuDTO> getMenuTree(String scope, Integer userType, Long userId) {
        List<String> perms = listPermCodes(userType, userId);
        boolean all = perms.contains(CommonConstants.PERM_ALL);
        Set<String> permSet = new java.util.HashSet<>(perms);

        // 导航菜单仅取目录/菜单（menu_type<3），可见且启用
        List<SysMenu> menus = sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getScope, scope)
                .lt(SysMenu::getMenuType, 3)
                .eq(SysMenu::getVisible, 1)
                .eq(SysMenu::getStatus, 1)
                .orderByAsc(SysMenu::getSortOrder));

        Map<Long, List<SysMenu>> byParent = menus.stream()
                .collect(Collectors.groupingBy(m -> m.getParentId() == null ? 0L : m.getParentId()));
        return buildFilteredTree(0L, byParent, all, permSet);
    }

    /** 递归构建按权限过滤后的菜单树：菜单节点需命中权限，目录节点需含可见子节点 */
    private List<SysMenuDTO> buildFilteredTree(Long parentId, Map<Long, List<SysMenu>> byParent,
                                               boolean all, Set<String> permSet) {
        List<SysMenu> children = byParent.get(parentId);
        if (children == null || children.isEmpty()) {
            return new ArrayList<>();
        }
        List<SysMenuDTO> result = new ArrayList<>();
        for (SysMenu menu : children) {
            SysMenuDTO dto = toMenuDTO(menu);
            if (menu.getMenuType() != null && menu.getMenuType() == 1) {
                // 目录：仅当存在可见子节点时保留
                List<SysMenuDTO> sub = buildFilteredTree(menu.getId(), byParent, all, permSet);
                if (!sub.isEmpty()) {
                    dto.setChildren(sub);
                    result.add(dto);
                }
            } else {
                // 菜单：无 perm_code 视为公共（如控制台）；否则需命中权限
                boolean permitted = all
                        || menu.getPermCode() == null || menu.getPermCode().isEmpty()
                        || permSet.contains(menu.getPermCode());
                if (permitted) {
                    dto.setChildren(buildFilteredTree(menu.getId(), byParent, all, permSet));
                    result.add(dto);
                }
            }
        }
        return result;
    }

    @Override
    public List<SysMenuDTO> getAllMenuTree(String scope) {
        List<SysMenu> menus = sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getScope, scope)
                .eq(SysMenu::getStatus, 1)
                .orderByAsc(SysMenu::getSortOrder));
        Map<Long, List<SysMenu>> byParent = menus.stream()
                .collect(Collectors.groupingBy(m -> m.getParentId() == null ? 0L : m.getParentId()));
        return buildFullTree(0L, byParent);
    }

    private List<SysMenuDTO> buildFullTree(Long parentId, Map<Long, List<SysMenu>> byParent) {
        List<SysMenu> children = byParent.get(parentId);
        if (children == null || children.isEmpty()) {
            return new ArrayList<>();
        }
        List<SysMenuDTO> result = new ArrayList<>();
        for (SysMenu menu : children) {
            SysMenuDTO dto = toMenuDTO(menu);
            dto.setChildren(buildFullTree(menu.getId(), byParent));
            result.add(dto);
        }
        return result;
    }

    private SysMenuDTO toMenuDTO(SysMenu menu) {
        SysMenuDTO dto = new SysMenuDTO();
        BeanUtils.copyProperties(menu, dto);
        return dto;
    }

    /** 删除某用户的权限缓存 key（userType：1平台员工 2商家账号） */
    private void evictPerms(Integer userType, Long userId) {
        String typeStr = userType != null && userType == USER_TYPE_MERCHANT
                ? CommonConstants.USER_TYPE_MERCHANT : CommonConstants.USER_TYPE_SYS;
        redisUtil.delete(CommonConstants.AUTH_PERMS_KEY_PREFIX + typeStr + ":" + userId);
    }
}
