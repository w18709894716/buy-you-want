package com.byw.api.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @NotBlank(message = "登录用户名不能为空")
    @Size(max = 50, message = "登录用户名过长")
    private String username;
    private String password;
    @Size(max = 50, message = "姓名过长")
    private String nickname;
    @Size(max = 20, message = "联系电话过长")
    private String phone;
    @Size(max = 100, message = "邮箱过长")
    private String email;
    @Size(max = 500, message = "头像地址过长")
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
