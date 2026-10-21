package com.byw.api.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductDTO implements Serializable {
    private Long id;
    @NotBlank(message = "商品名称不能为空")
    @Size(max = 200, message = "商品名称过长")
    private String name;
    @Size(max = 500, message = "副标题过长")
    private String subtitle;
    private Long shopId;
    /** 归属店铺名称（详情接口回填，非入库字段） */
    private String shopName;
    @NotNull(message = "请选择商品分类")
    private Long categoryId;
    private Long brandId;
    @Size(max = 500, message = "主图地址过长")
    private String mainImage;
    private String subImages;
    private String detailHtml;
    private Integer status;
    /** 审核状态：0待审核 1审核通过 2审核驳回 */
    private Integer auditStatus;
    /** 审核驳回原因 */
    private String rejectReason;
    private Integer salesCount;
    private BigDecimal minPrice;
    /** 该商品所有 SKU 库存之和 */
    private Integer totalStock;
    private LocalDateTime createdAt;
    /** 规格列表：级联校验（价格必填、库存不能为负） */
    @Valid
    private List<SkuDTO> skus;
}
