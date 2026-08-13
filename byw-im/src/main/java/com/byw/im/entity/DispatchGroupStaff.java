package com.byw.im.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM 分流分组-客服关联（weight 为客服在该组的接待权重，组内加权均衡分配）
 */
@Data
@TableName("t_im_dispatch_group_staff")
public class DispatchGroupStaff {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分流分组ID */
    private Long groupId;

    /** 客服ID（merchant_account.id） */
    private Long staffId;

    /** 接待权重（默认1） */
    private Integer weight;

    private LocalDateTime createdAt;
}
