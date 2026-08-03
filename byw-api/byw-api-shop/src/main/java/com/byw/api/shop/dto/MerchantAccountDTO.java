package com.byw.api.shop.dto;

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
    private String username;

    /** 密码（BCrypt，仅内部流转） */
    private String password;

    /** 商家真实姓名/企业联系人 */
    private String realName;

    /** 联系电话 */
    private String phone;

    /** 入驻类型 1个人 2企业 */
    private Integer merchantType;

    /** 意向店铺名称（审核通过后用于建店） */
    private String shopName;

    /** 企业名称（企业入驻） */
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
    private String rejectReason;

    /** 账号状态 0禁用 1正常 */
    private Integer status;

    private LocalDateTime createdAt;

    /** 子账号分配的预设角色ID列表（新建/改角色入参，仅商家子账号使用） */
    private List<Long> roleIds;

    /** 子账号绑定的角色名称（顿号连接，列表展示用，由 BFF 聚合填充） */
    private String roleNames;
}
