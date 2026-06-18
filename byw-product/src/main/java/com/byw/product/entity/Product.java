package com.byw.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_product")
public class Product implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String subtitle;

    private Long categoryId;

    private Long brandId;

    private String mainImage;

    private String subImages;

    private String detailHtml;

    private Integer status;

    private Integer salesCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
