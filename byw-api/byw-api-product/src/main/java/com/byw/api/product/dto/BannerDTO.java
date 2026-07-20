package com.byw.api.product.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class BannerDTO implements Serializable {

    private Long id;
    /** Banner标题 */
    private String title;
    /** 轮播图片URL */
    private String imageUrl;
    /** 跳转类型:1搜索关键词 2商品详情 3商品分类 4自定义链接 */
    private Integer linkType;
    /** 跳转值 */
    private String linkValue;
    /** 排序 */
    private Integer sortOrder;
    /** 0禁用 1启用 */
    private Integer status;
    /** 上线时间 */
    private LocalDateTime startTime;
    /** 下线时间 */
    private LocalDateTime endTime;
    private LocalDateTime createdAt;
}
