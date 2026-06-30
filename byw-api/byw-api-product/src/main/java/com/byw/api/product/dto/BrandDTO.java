package com.byw.api.product.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class BrandDTO implements Serializable {

    private Long id;
    private String name;
    private String firstLetter;
    private String logo;
    private Integer sortOrder;
    private Integer status;
    private Integer productCount;
    private LocalDateTime createdAt;
}
