package com.byw.api.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class BrandDTO implements Serializable {

    private Long id;
    @NotBlank(message = "品牌名称不能为空")
    @Size(max = 100, message = "品牌名称过长")
    private String name;
    @Size(max = 10, message = "首字母过长")
    private String firstLetter;
    @Size(max = 500, message = "品牌Logo地址过长")
    private String logo;
    private Integer sortOrder;
    private Integer status;
    private Integer productCount;
    private LocalDateTime createdAt;
}
