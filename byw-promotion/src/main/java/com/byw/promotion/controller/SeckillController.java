package com.byw.promotion.controller;

import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.ProductDTO;
import com.byw.api.product.dto.SkuDTO;
import com.byw.api.promotion.dto.SeckillActivityDetailDTO;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import com.byw.promotion.entity.SeckillActivity;
import com.byw.promotion.entity.SeckillActivityItem;
import com.byw.promotion.service.SeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "秒杀", description = "秒杀活动管理")
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;
    private final ProductFeignClient productFeignClient;

    @Operation(summary = "获取秒杀活动列表")
    @GetMapping("/list")
    public R<List<SeckillActivity>> list() {
        return R.ok(seckillService.getSeckillList());
    }

    @Operation(summary = "参与秒杀")
    @PostMapping("/seckill/{activityId}/{itemId}")
    @RequireLogin
    public R<Void> seckill(@PathVariable Long activityId, @PathVariable Long itemId,
                           @RequestParam(value = "addressId", required = false) Long addressId) {
        seckillService.seckill(activityId, itemId, UserContext.getUserId(), addressId);
        return R.ok();
    }

    @Operation(summary = "秒杀活动列表(含商品信息)")
    @GetMapping("/list/detail")
    public R<List<SeckillActivityDetailDTO>> listDetail() {
        List<SeckillActivity> activities = seckillService.getSeckillList();
        LocalDateTime now = LocalDateTime.now();
        Map<Long, ProductDTO> productCache = new HashMap<>();
        List<SeckillActivityDetailDTO> dtoList = new ArrayList<>();
        for (SeckillActivity activity : activities) {
            SeckillActivityDetailDTO dto = new SeckillActivityDetailDTO();
            dto.setId(activity.getId());
            dto.setName(activity.getName());
            dto.setStartTime(activity.getStartTime());
            dto.setEndTime(activity.getEndTime());
            // 根据时间动态计算状态：0=未开始 1=进行中 2=已结束
            if (now.isBefore(activity.getStartTime())) {
                dto.setStatus(0);
            } else if (now.isAfter(activity.getEndTime())) {
                dto.setStatus(2);
            } else {
                dto.setStatus(1);
            }
            List<SeckillActivityDetailDTO.SeckillItemDetailDTO> itemDtos = new ArrayList<>();
            for (SeckillActivityItem item : seckillService.getItemsByActivityId(activity.getId())) {
                itemDtos.add(buildItemDetail(item, productCache));
            }
            dto.setItems(itemDtos);
            dtoList.add(dto);
        }
        return R.ok(dtoList);
    }

    /**
     * 构建商品条目详情（填充商品名/图/SKU名/原价）
     */
    private SeckillActivityDetailDTO.SeckillItemDetailDTO buildItemDetail(SeckillActivityItem item,
                                                                          Map<Long, ProductDTO> productCache) {
        SeckillActivityDetailDTO.SeckillItemDetailDTO itemDto = new SeckillActivityDetailDTO.SeckillItemDetailDTO();
        itemDto.setItemId(item.getId());
        itemDto.setProductId(item.getProductId());
        itemDto.setSkuId(item.getSkuId());
        itemDto.setSeckillPrice(item.getSeckillPrice());
        itemDto.setTotalStock(item.getTotalStock());
        itemDto.setAvailableStock(item.getAvailableStock());

        ProductDTO product = productCache.get(item.getProductId());
        if (product == null && !productCache.containsKey(item.getProductId())) {
            try {
                R<ProductDTO> productResult = productFeignClient.getProductById(item.getProductId());
                product = productResult != null ? productResult.getData() : null;
            } catch (Exception e) {
                product = null;
            }
            productCache.put(item.getProductId(), product);
        }
        if (product != null) {
            itemDto.setProductName(product.getName());
            itemDto.setProductImage(product.getMainImage());
            itemDto.setOriginalPrice(product.getMinPrice());
            if (product.getSkus() != null) {
                for (SkuDTO sku : product.getSkus()) {
                    if (sku.getId() != null && sku.getId().equals(item.getSkuId())) {
                        itemDto.setSkuName(sku.getSkuName());
                        // 原价优先取对应SKU的售价
                        itemDto.setOriginalPrice(sku.getPrice());
                        break;
                    }
                }
            }
        } else {
            itemDto.setProductName("商品 " + item.getProductId());
        }
        return itemDto;
    }
}
