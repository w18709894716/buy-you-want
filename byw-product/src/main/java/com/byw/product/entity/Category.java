package com.byw.product.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_category")
public class Category implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 100, message = "分类名称过长")
    private String name;

    private Long parentId;

    private Integer level;

    @Min(value = 0, message = "排序值不能为负")
    private Integer sortOrder;

    @Size(max = 500, message = "分类图标地址过长")
    private String icon;

    @Min(value = 0, message = "显示标记只能是 0 或 1")
    @Max(value = 1, message = "显示标记只能是 0 或 1")
    private Integer isShow;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
