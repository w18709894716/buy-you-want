package com.byw.review.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_review_image")
public class ReviewImage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reviewId;

    private String imageUrl;

    /** 0初评 1追评 */
    private Integer type;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
