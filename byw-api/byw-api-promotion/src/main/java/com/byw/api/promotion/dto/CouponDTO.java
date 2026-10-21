package com.byw.api.promotion.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券 DTO：既用于新增/修改入参，也用于列表与详情返回。
 * 校验注解只在以 {@code @Valid} 绑定请求体时生效，作为返回值序列化时不参与。
 */
@Data
public class CouponDTO implements Serializable {
    private Long id;
    @NotBlank(message = "优惠券名称不能为空")
    @Size(max = 100, message = "优惠券名称过长")
    private String name;
    /** 1满减券 2折扣券 3无门槛券 */
    @NotNull(message = "请选择优惠券类型")
    @Min(value = 1, message = "优惠券类型不合法")
    @Max(value = 3, message = "优惠券类型不合法")
    private Integer type;
    @NotNull(message = "请填写优惠面额或折扣")
    @DecimalMin(value = "0.01", message = "优惠面额或折扣必须大于 0")
    private BigDecimal discountValue;
    private BigDecimal minAmount;
    @NotNull(message = "请填写发放总量")
    @Min(value = 1, message = "发放总量必须大于 0")
    private Integer totalCount;
    private Integer claimedCount;
    @NotNull(message = "请选择开始时间")
    private LocalDateTime startTime;
    @NotNull(message = "请选择结束时间")
    private LocalDateTime endTime;
    private Integer status;
    /** 是否新人专享：0普通 1新人专享 */
    private Integer newUser;
    /** 归属店铺ID：null或0平台券(全场通用) 其他为店铺券 */
    private Long shopId;
}
