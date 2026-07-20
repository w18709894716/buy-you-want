package com.byw.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_banner")
public class Banner implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Banner标题(后台识别/无图时展示) */
    private String title;

    /** 轮播图片URL(MinIO) */
    private String imageUrl;

    /** 跳转类型:1搜索关键词 2商品详情 3商品分类 4自定义链接 */
    private Integer linkType;

    /** 跳转值:关键词/商品ID/分类名/URL */
    private String linkValue;

    /** 排序,越小越靠前 */
    private Integer sortOrder;

    /** 0禁用 1启用 */
    private Integer status;

    /** 上线时间(空表示立即上线) */
    private LocalDateTime startTime;

    /** 下线时间(空表示永久有效) */
    private LocalDateTime endTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
