package com.byw.api.product.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductDTO implements Serializable {
    private Long id;
    private String name;
    private String subtitle;
    private Long shopId;
    /** 归属店铺名称（详情接口回填，非入库字段） */
    private String shopName;
    private Long categoryId;
    private Long brandId;
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
    private List<SkuDTO> skus;
}
