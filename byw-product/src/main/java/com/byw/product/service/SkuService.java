package com.byw.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.byw.api.product.dto.SkuStockDeductDTO;
import com.byw.product.entity.Sku;

import java.util.List;

public interface SkuService extends IService<Sku> {

    Boolean deductStock(List<SkuStockDeductDTO> deductList);

    Boolean lockStock(List<SkuStockDeductDTO> deductList);

    Boolean releaseStock(List<SkuStockDeductDTO> deductList);

    Boolean consumeStock(List<SkuStockDeductDTO> deductList);

    Boolean restoreStock(List<SkuStockDeductDTO> deductList);

    /**
     * 批量按增量调整库存（商家编辑商品时用），delta 可为负。
     *
     * <p>整体一个事务：任一 SKU 调整后库存会为负则全部回滚，不留半改状态。
     *
     * @param deltas skuId → 库存增量
     */
    void adjustStockBatch(java.util.Map<Long, Integer> deltas);
}
