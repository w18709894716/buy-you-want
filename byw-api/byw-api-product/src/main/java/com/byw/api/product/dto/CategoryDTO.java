package com.byw.api.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CategoryDTO implements Serializable {

    private Long id;
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 100, message = "分类名称过长")
    private String name;
    private Long parentId;
    private Integer level;
    private Integer sortOrder;
    @Size(max = 500, message = "分类图标地址过长")
    private String icon;
    private Integer isShow;
    private LocalDateTime createdAt;
    private List<CategoryDTO> children;
}
