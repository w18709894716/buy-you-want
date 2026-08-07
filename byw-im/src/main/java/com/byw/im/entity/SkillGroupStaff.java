package com.byw.im.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM 技能组-客服关联
 */
@Data
@TableName("t_im_skill_group_staff")
public class SkillGroupStaff {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 技能组ID */
    private Long groupId;

    /** 客服ID（merchant_account.id） */
    private Long staffId;

    private LocalDateTime createdAt;
}