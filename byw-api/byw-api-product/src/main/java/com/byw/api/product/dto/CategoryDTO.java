package com.byw.api.product.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CategoryDTO implements Serializable {

    private Long id;
    private String name;
    private Long parentId;
    private Integer level;
    private Integer sortOrder;
    private String icon;
    private Integer isShow;
    private LocalDateTime createdAt;
    private List<CategoryDTO> children;
}
