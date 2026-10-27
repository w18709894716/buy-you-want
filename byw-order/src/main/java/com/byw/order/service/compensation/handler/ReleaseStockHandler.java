package com.byw.order.service.compensation.handler;

import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.SkuStockDeductDTO;
import com.byw.common.core.result.R;
import com.byw.order.entity.CompensationTask;
import com.byw.order.service.compensation.CompensationHandler;
import com.byw.order.service.compensation.CompensationPayloads;
import com.byw.order.service.compensation.CompensationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 下单失败后撤回库存预占。
 *
 * <p>幂等性：商品侧 {@code releaseStock} 带 {@code lock_stock >= quantity} 的 CAS，
 * 重复执行不会把可售库存加两次，只会因不满足条件而报错。也就是说这个动作"不会算错但会报错"，
 * 所以重试是安全的；若第一次实际已成功、只是在写回任务状态前进程退出，
 * 重放会失败并最终落到"需人工"——属于可接受的误报，人工看一眼库存即可。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReleaseStockHandler implements CompensationHandler {

    private final ProductFeignClient productFeignClient;
    private final ObjectMapper objectMapper;

    @Override
    public CompensationType type() {
        return CompensationType.RELEASE_STOCK;
    }

    @Override
    public void execute(CompensationTask task) throws Exception {
        CompensationPayloads.ReleaseStock payload =
                objectMapper.readValue(task.getPayload(), CompensationPayloads.ReleaseStock.class);
        if (payload.items() == null || payload.items().isEmpty()) {
            throw new IllegalArgumentException("缺少待释放的 SKU 明细");
        }
        List<SkuStockDeductDTO> items = payload.items().stream()
                .map(i -> new SkuStockDeductDTO(i.skuId(), i.quantity()))
                .toList();
        R<Boolean> result = productFeignClient.releaseStock(items);
        if (result == null || !result.isSuccess() || !Boolean.TRUE.equals(result.getData())) {
            throw new IllegalStateException("撤回库存预占未成功: "
                    + (result == null ? "响应为空" : result.getCode() + "/" + result.getMessage()));
        }
        log.info("下单失败已撤回库存预占: orderNo={}, items={}", task.getOrderNo(), items.size());
    }
}
