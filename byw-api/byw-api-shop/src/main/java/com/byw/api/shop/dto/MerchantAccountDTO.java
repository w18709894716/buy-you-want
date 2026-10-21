package com.byw.api.shop.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商家账号（入驻申请与商家登录主体）。
 */
@Data
public class MerchantAccountDTO implements Serializable {

    private Long id;

    /** 登录用户名 */
    @NotBlank(message = "登录用户名不能为空")
    @Size(min = 4, max = 50, message = "登录用户名需为 4~50 位")
    private String username;

    /** 密码（BCrypt，仅内部流转） */
    /** 密码：入驻申请与新建员工都必须提供（长度对齐表列 varchar(200)） */
    @NotBlank(message = "登录密码不能为空")
    @Size(max = 200, message = "登录密码过长")
    private String password;

    /** 商家真实姓名/企业联系人 */
    @Size(max = 50, message = "姓名过长")
    private String realName;

    /** 联系电话 */
    @Size(max = 20, message = "联系电话过长")
    private String phone;

    /** 入驻类型 1个人 2企业 */
    @Min(value = 1, message = "入驻类型只能是 1个人/2企业")
    @Max(value = 2, message = "入驻类型只能是 1个人/2企业")
    private Integer merchantType;

    /** 意向店铺名称（审核通过后用于建店） */
    @Size(max = 100, message = "意向店铺名称过长")
    private String shopName;

    /** 企业名称（企业入驻） */
    @Size(max = 128, message = "企业名称过长")
    private String companyName;

    /** 身份证人像面图片URL（个人入驻） */
    private String idCardFront;

    /** 身份证国徽面图片URL（个人入驻） */
    private String idCardBack;

    /** 营业执照图片URL（企业入驻） */
    private String businessLicense;

    /** 已签署入驻协议 0否 1是 */
    private Integer agreementSigned;

    /** 发起申请的C端用户ID（历史字段：入驻已与C端账号解耦，不再写入） */
    private Long applyUserId;

    /** 关联店铺ID（审核通过后回填） */
    private Long shopId;

    /** 主账号ID：NULL=主账号，非NULL=子账号(员工) */
    private Long parentId;

    /** 角色 merchant_owner / merchant_staff */
    private String role;

    /** 入驻审核状态 0待审核 1通过 2驳回 */
    private Integer auditStatus;

    /** 驳回原因 */
    @Size(max = 255, message = "驳回原因过长")
    private String rejectReason;

    /** 账号状态 0禁用 1正常 */
    private Integer status;

    private LocalDateTime createdAt;

    /** 子账号分配的预设角色ID列表（新建/改角色入参，仅商家子账号使用） */
    private List<Long> roleIds;

    /** 子账号绑定的角色名称（顿号连接，列表展示用，由 BFF 聚合填充） */
    private String roleNames;
}
