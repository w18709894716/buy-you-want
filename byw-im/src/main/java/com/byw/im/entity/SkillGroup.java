package com.byw.im.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM 技能组：按意图（售前/售后/物流等）对客服分组，支持关键词路由。
 */
@Data
@TableName("t_im_skill_group")
public class SkillGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 店铺ID */
    private Long shopId;

    /** 技能组名称（售前/售后/物流…） */
    @NotBlank(message = "技能组名称不能为空")
    @Size(max = 50, message = "技能组名称过长")
    private String groupName;

    /** 路由关键词（逗号分隔，匹配用户消息首句） */
    @Size(max = 500, message = "路由关键词过长")
    private String keywords;

    /** 优先级（数字越小越优先匹配） */
    private Integer sort;

    /** 状态 0禁用 1启用 */
    @Min(value = 0, message = "状态只能是 0 或 1")
    @Max(value = 1, message = "状态只能是 0 或 1")
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}