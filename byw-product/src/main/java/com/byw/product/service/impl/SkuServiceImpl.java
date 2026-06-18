package com.byw.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.byw.api.product.dto.SkuStockDeductDTO;
import com.byw.common.core.exception.BusinessException;
import com.byw.product.entity.Sku;
import com.byw.product.mapper.SkuMapper;
import com.byw.product.service.SkuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SkuServiceImpl extends ServiceImpl<SkuMapper, Sku> implements SkuService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deductStock(List<SkuStockDeductDTO> deductList) {
        for (SkuStockDeductDTO dto : deductList) {
            // stock >= quantity then deduct: stock - quantity, lockStock unchanged
            LambdaUpdateWrapper<Sku> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Sku::getId, dto.getSkuId())
                    .ge(Sku::getStock, dto.getQuantity())
                    .setSql("stock = stock - " + dto.getQuantity());
            int rows = baseMapper.update(null, wrapper);
            if (rows == 0) {
                throw new BusinessException("库存不足，SKU ID: " + dto.getSkuId());
            }
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean lockStock(List<SkuStockDeductDTO> deductList) {
        for (SkuStockDeductDTO dto : deductList) {
            // lock stock: stock - quantity, lockStock + quantity
            LambdaUpdateWrapper<Sku> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Sku::getId, dto.getSkuId())
                    .ge(Sku::getStock, dto.getQuantity())
                    .setSql("stock = stock - " + dto.getQuantity())
                    .setSql("lock_stock = lock_stock + " + dto.getQuantity());
            int rows = baseMapper.update(null, wrapper);
            if (rows == 0) {
                throw new BusinessException("库存不足，无法锁定，SKU ID: " + dto.getSkuId());
            }
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean releaseStock(List<SkuStockDeductDTO> deductList) {
        for (SkuStockDeductDTO dto : deductList) {
            // release: stock + quantity, lockStock - quantity
            LambdaUpdateWrapper<Sku> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Sku::getId, dto.getSkuId())
                    .ge(Sku::getLockStock, dto.getQuantity())
                    .setSql("stock = stock + " + dto.getQuantity())
                    .setSql("lock_stock = lock_stock - " + dto.getQuantity());
            int rows = baseMapper.update(null, wrapper);
            if (rows == 0) {
                throw new BusinessException("锁定库存不足，无法释放，SKU ID: " + dto.getSkuId());
            }
        }
        return true;
    }
}
