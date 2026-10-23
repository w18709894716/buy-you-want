package com.byw.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.byw.api.product.dto.SkuStockDeductDTO;
import com.byw.common.core.exception.BusinessException;
import com.byw.product.entity.Sku;
import com.byw.product.mapper.SkuMapper;
import com.byw.product.service.SkuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkuServiceImpl extends ServiceImpl<SkuMapper, Sku> implements SkuService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deductStock(List<SkuStockDeductDTO> deductList) {
        validateDeductList(deductList);
        for (SkuStockDeductDTO dto : deductList) {
            // 查询当前库存
            Sku sku = baseMapper.selectById(dto.getSkuId());
            if (sku == null) {
                throw new BusinessException("商品不存在");
            }
            if (sku.getStock() < dto.getQuantity()) {
                throw new BusinessException("库存不足");
            }
            // 乐观锁扣减：stock >= quantity then deduct
            LambdaUpdateWrapper<Sku> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Sku::getId, dto.getSkuId())
                    .ge(Sku::getStock, dto.getQuantity())
                    .setSql("stock = stock - " + dto.getQuantity());
            int rows = baseMapper.update(null, wrapper);
            if (rows == 0) {
                throw new BusinessException("商品库存不足，请减少购买数量");
            }
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean lockStock(List<SkuStockDeductDTO> deductList) {
        validateDeductList(deductList);
        for (SkuStockDeductDTO dto : deductList) {
            // lock stock: stock - quantity, lockStock + quantity
            LambdaUpdateWrapper<Sku> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Sku::getId, dto.getSkuId())
                    .ge(Sku::getStock, dto.getQuantity())
                    .setSql("stock = stock - " + dto.getQuantity())
                    .setSql("lock_stock = lock_stock + " + dto.getQuantity());
            int rows = baseMapper.update(null, wrapper);
            if (rows == 0) {
                // 影响行数为 0 = 库存不足或 SKU 不存在。skuId 只进日志，不进异常消息：
                // 这句会一路透到买家页面，内部标识对用户没有意义（见文案规范）
                log.info("锁定库存失败（库存不足或 SKU 不存在）: skuId={}, quantity={}",
                        dto.getSkuId(), dto.getQuantity());
                throw new BusinessException("库存不足，请调整购买数量后重试");
            }
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean releaseStock(List<SkuStockDeductDTO> deductList) {
        validateDeductList(deductList);
        for (SkuStockDeductDTO dto : deductList) {
            // release: stock + quantity, lockStock - quantity
            LambdaUpdateWrapper<Sku> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Sku::getId, dto.getSkuId())
                    .ge(Sku::getLockStock, dto.getQuantity())
                    .setSql("stock = stock + " + dto.getQuantity())
                    .setSql("lock_stock = lock_stock - " + dto.getQuantity());
            int rows = baseMapper.update(null, wrapper);
            if (rows == 0) {
                // lock_stock 不够扣，通常是重复释放或预占记录与库存不同步，属于需要排查的异常
                log.warn("释放库存失败（lock_stock 不足）: skuId={}, quantity={}",
                        dto.getSkuId(), dto.getQuantity());
                throw new BusinessException("库存释放失败，请稍后重试");
            }
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean consumeStock(List<SkuStockDeductDTO> deductList) {
        validateDeductList(deductList);
        for (SkuStockDeductDTO dto : deductList) {
            // 核销：支付成功后将下单预占真正售出，stock 已在 lockStock 时预减，此处仅减 lock_stock
            LambdaUpdateWrapper<Sku> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Sku::getId, dto.getSkuId())
                    .ge(Sku::getLockStock, dto.getQuantity())
                    .setSql("lock_stock = lock_stock - " + dto.getQuantity());
            int rows = baseMapper.update(null, wrapper);
            if (rows == 0) {
                // 支付成功后核销预占，失败意味着 lock_stock 与订单不符（重复回调或超卖兜底），必须留痕
                log.warn("核销库存失败（lock_stock 不足）: skuId={}, quantity={}",
                        dto.getSkuId(), dto.getQuantity());
                throw new BusinessException("库存核销失败，请稍后重试");
            }
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean restoreStock(List<SkuStockDeductDTO> deductList) {
        validateDeductList(deductList);
        for (SkuStockDeductDTO dto : deductList) {
            // 回补：已支付订单取消/售后退款，货未售出退回可售库存，仅加 stock（lock_stock 已在核销时清零）
            LambdaUpdateWrapper<Sku> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Sku::getId, dto.getSkuId())
                    .setSql("stock = stock + " + dto.getQuantity());
            int rows = baseMapper.update(null, wrapper);
            if (rows == 0) {
                log.warn("回补库存失败（SKU 不存在）: skuId={}, quantity={}",
                        dto.getSkuId(), dto.getQuantity());
                throw new BusinessException("库存回补失败，请稍后重试");
            }
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustStockBatch(Map<Long, Integer> deltas) {
        if (deltas == null || deltas.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, Integer> entry : deltas.entrySet()) {
            Integer delta = entry.getValue();
            if (entry.getKey() == null || delta == null || delta == 0) {
                continue;
            }
            // WHERE 带 stock + delta >= 0：影响行数为 0 说明期间已被订单占用，调整后会变负数
            if (baseMapper.adjustStock(entry.getKey(), delta) == 0) {
                throw new BusinessException("库存已被订单占用，请刷新页面后重试");
            }
        }
    }

    /**
     * 库存操作入参自检：数量必须为正整数。
     *
     * <p>扣减/预占使用 {@code stock = stock - quantity} 这类 SQL 表达式，数量为负时表达式会变成加法，
     * 因此即使调用方（订单/售后/秒杀）已经校验过，服务侧仍必须再挡一次。
     */
    private void validateDeductList(List<SkuStockDeductDTO> deductList) {
        if (deductList == null || deductList.isEmpty()) {
            return; // 空列表视为无操作，保持与原实现一致
        }
        for (SkuStockDeductDTO dto : deductList) {
            if (dto == null || dto.getSkuId() == null) {
                throw new BusinessException("库存操作参数不合法");
            }
            if (dto.getQuantity() == null || dto.getQuantity() <= 0) {
                throw new BusinessException("库存操作数量必须大于 0");
            }
        }
    }
}
