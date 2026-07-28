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

    /** 归属店铺ID（多租户维度） */
    private Long shopId;

    private Long categoryId;

    private Long brandId;

    private String mainImage;

    private String subImages;

    private String detailHtml;

    private Integer status;

    /** 审核状态：0待审核 1审核通过 2审核驳回 */
    private Integer auditStatus;

    /** 审核驳回原因（auditStatus=2 时有效） */
    private String rejectReason;

    private Integer salesCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
