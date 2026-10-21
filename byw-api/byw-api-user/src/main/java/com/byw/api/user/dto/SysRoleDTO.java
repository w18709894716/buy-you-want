package com.byw.api.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 角色（t_sys_role）传输对象。
 */
@Data
public class SysRoleDTO implements Serializable {

    private Long id;
    @NotBlank(message = "角色标识不能为空")
    @Size(max = 50, message = "角色标识过长")
    private String roleCode;
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称过长")
    private String roleName;
    /** platform 平台 / merchant 商家（平台端 BFF 会在方法体内注入，因此只做格式校验、不设必填） */
    @Pattern(regexp = "platform|merchant", message = "角色适用端只能是 platform 或 merchant")
    private String scope;
    private Long shopId;
    /** 内置预设 0否 1是 */
    private Integer isPreset;
    @Size(max = 255, message = "备注过长")
    private String remark;
    private Integer status;
    /** 绑定该角色的用户数（列表展示用） */
    private Integer userCount;
}
