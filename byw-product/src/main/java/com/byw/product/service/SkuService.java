package com.byw.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.byw.api.product.dto.SkuStockDeductDTO;
import com.byw.product.entity.Sku;

import java.util.List;

public interface SkuService extends IService<Sku> {

    Boolean deductStock(List<SkuStockDeductDTO> deductList);

    Boolean lockStock(List<SkuStockDeductDTO> deductList);

    Boolean releaseStock(List<SkuStockDeductDTO> deductList);
}
