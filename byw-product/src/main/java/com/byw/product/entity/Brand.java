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
@TableName("t_brand")
public class Brand implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "品牌名称不能为空")
    @Size(max = 100, message = "品牌名称过长")
    private String name;

    @Size(max = 10, message = "首字母过长")
    private String firstLetter;

    @Size(max = 500, message = "品牌Logo地址过长")
    private String logo;

    private Integer sortOrder;

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
