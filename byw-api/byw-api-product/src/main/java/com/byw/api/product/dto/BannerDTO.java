package com.byw.api.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class BannerDTO implements Serializable {

    private Long id;
    /** Banner标题 */
    @NotBlank(message = "Banner标题不能为空")
    @Size(max = 200, message = "Banner标题过长")
    private String title;
    /** 轮播图片URL */
    @Size(max = 500, message = "图片地址过长")
    private String imageUrl;
    /** 跳转类型:1搜索关键词 2商品详情 3商品分类 4自定义链接 */
    @Min(value = 1, message = "跳转类型只能是 1搜索/2商品/3分类/4链接")
    @Max(value = 4, message = "跳转类型只能是 1搜索/2商品/3分类/4链接")
    private Integer linkType;
    /** 跳转值 */
    @Size(max = 500, message = "跳转值过长")
    private String linkValue;
    /** 展示位置:0轮播 1右侧活动位 */
    @Min(value = 0, message = "展示位置只能是 0轮播/1活动位")
    @Max(value = 1, message = "展示位置只能是 0轮播/1活动位")
    private Integer position;
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
