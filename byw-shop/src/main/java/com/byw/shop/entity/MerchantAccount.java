package com.byw.shop.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_merchant_account")
public class MerchantAccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录用户名 */
    private String username;

    /** 密码（BCrypt） */
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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
