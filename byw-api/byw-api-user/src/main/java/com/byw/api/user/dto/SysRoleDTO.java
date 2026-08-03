package com.byw.api.user.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 角色（t_sys_role）传输对象。
 */
@Data
public class SysRoleDTO implements Serializable {

    private Long id;
    private String roleCode;
    private String roleName;
    /** platform 平台 / merchant 商家 */
    private String scope;
    private Long shopId;
    /** 内置预设 0否 1是 */
    private Integer isPreset;
    private String remark;
    private Integer status;
}
