package com.byw.im.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM FAQ 知识库
 */
@Data
@TableName("t_im_faq")
public class Faq {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 店铺ID */
    private Long shopId;

    /** 问题 */
    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题过长")
    private String question;

    /** 答案 */
    @NotBlank(message = "答案不能为空")
    @Size(max = 2000, message = "答案过长")
    private String answer;

    /** 状态 1启用 0禁用 */
    private Integer status;

    /** 排序（升序） */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}