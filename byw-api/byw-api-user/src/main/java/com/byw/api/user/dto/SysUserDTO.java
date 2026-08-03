package com.byw.api.user.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台员工（t_sys_user）传输对象，兼作 RBAC Feign 契约的入参/出参。
 */
@Data
public class SysUserDTO implements Serializable {

    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String phone;
    private String email;
    private String avatar;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private Long createdBy;
    private LocalDateTime createdAt;

    /** 已分配的角色ID列表（编辑回显/分配角色入参） */
    private List<Long> roleIds;
    /** 角色名称展示（列表用） */
    private String roleNames;
}
