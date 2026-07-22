package com.byw.promotion.feign;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.ProductDTO;
import com.byw.api.product.dto.SkuDTO;
import com.byw.api.promotion.PromotionFeignClient;
import com.byw.api.promotion.dto.CouponDTO;
import com.byw.api.promotion.dto.SeckillActivityDetailDTO;
import com.byw.api.promotion.dto.UserCouponDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.promotion.entity.Coupon;
import com.byw.promotion.entity.SeckillActivity;
import com.byw.promotion.entity.SeckillActivityItem;
import com.byw.promotion.mapper.CouponMapper;
import com.byw.promotion.mapper.SeckillActivityMapper;
import com.byw.promotion.service.CouponService;
import com.byw.promotion.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/feign/promotion")
@RequiredArgsConstructor
public class PromotionFeignImpl implements PromotionFeignClient {

    private final CouponService couponService;
    private final SeckillService seckillService;
    private final CouponMapper couponMapper;
    private final SeckillActivityMapper seckillActivityMapper;
    private final ProductFeignClient productFeignClient;

    @Override
    @PostMapping("/coupon/use")
    public R<BigDecimal> useCoupon(@RequestParam("couponId") Long couponId,
                                    @RequestParam("userId") Long userId,
                                    @RequestParam("orderAmount") BigDecimal orderAmount) {
        return R.ok(couponService.useCoupon(couponId, userId, orderAmount));
    }

    @Override
    @PostMapping("/coupon/release")
    public R<Boolean> releaseCoupon(@RequestParam("couponId") Long couponId,
                                     @RequestParam("userId") Long userId) {
        couponService.releaseCoupon(couponId, userId);
        return R.ok(true);
    }

    @Override
    @GetMapping("/coupon/{couponId}")
    public R<CouponDTO> getCouponById(@PathVariable("couponId") Long couponId) {
        return R.ok(couponService.getCouponById(couponId));
    }

    @Override
    @GetMapping("/coupon/user-coupons")
    public R<List<UserCouponDTO>> listUserCoupons(@RequestParam("userId") Long userId,
                                                   @RequestParam(value = "status", defaultValue = "0") Integer status) {
        return R.ok(couponService.listUserCoupons(userId, status));
    }

    @Override
    @GetMapping("/coupon/list")
    public R<PageResult<CouponDTO>> listCoupons(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                                @RequestParam(value = "status", required = false) Integer status) {
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Coupon::getStatus, status);
        }
        wrapper.orderByDesc(Coupon::getCreatedAt);
        IPage<Coupon> page = couponMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<CouponDTO> dtoList = page.getRecords().stream().map(coupon -> {
            CouponDTO dto = new CouponDTO();
            BeanUtils.copyProperties(coupon, dto);
            return dto;
        }).collect(Collectors.toList());
        return R.ok(PageResult.of(dtoList, page.getTotal(), pageNum, pageSize));
    }

    @Override
    @PostMapping("/coupon")
    public R<Long> createCoupon(@RequestBody CouponDTO couponDTO) {
        Coupon coupon = new Coupon();
        BeanUtils.copyProperties(couponDTO, coupon);
        couponMapper.insert(coupon);
        return R.ok(coupon.getId());
    }

    @Override
    @PutMapping("/coupon/{couponId}")
    public R<Boolean> updateCoupon(@PathVariable("couponId") Long couponId, @RequestBody CouponDTO couponDTO) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) return R.fail("优惠券不存在");
        BeanUtils.copyProperties(couponDTO, coupon);
        coupon.setId(couponId);
        return R.ok(couponMapper.updateById(coupon) > 0);
    }

    @Override
    @DeleteMapping("/coupon/{couponId}")
    public R<Boolean> deleteCoupon(@PathVariable("couponId") Long couponId) {
        return R.ok(couponMapper.deleteById(couponId) > 0);
    }

    @Override
    @GetMapping("/seckill/list")
    public R<List<SeckillActivityDTO>> listSeckillActivities() {
        // 管理端：返回所有状态的活动（含商品条目）
        List<SeckillActivity> activities = seckillService.getAllSeckillList();
        List<SeckillActivityDTO> dtoList = activities.stream()
                .map(activity -> toActivityDTO(activity, seckillService.getItemsByActivityId(activity.getId())))
                .collect(Collectors.toList());
        return R.ok(dtoList);
    }

    @Override
    @GetMapping("/seckill/{activityId}")
    public R<SeckillActivityDTO> getSeckillActivityById(@PathVariable("activityId") Long activityId) {
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (activity == null) return R.fail("秒杀活动不存在");
        return R.ok(toActivityDTO(activity, seckillService.getItemsByActivityId(activityId)));
    }

    @Override
    @PostMapping("/seckill")
    public R<Long> createSeckillActivity(@RequestBody SeckillActivityDTO activityDTO) {
        int itemCount = activityDTO.getItems() == null ? 0 : activityDTO.getItems().size();
        log.info("创建秒杀活动请求: name={}, items={}, startTime={}, endTime={}",
                activityDTO.getName(), itemCount, activityDTO.getStartTime(), activityDTO.getEndTime());
        try {
            SeckillActivity activity = new SeckillActivity();
            activity.setName(activityDTO.getName());
            activity.setStartTime(activityDTO.getStartTime());
            activity.setEndTime(activityDTO.getEndTime());
            List<SeckillActivityItem> items = toItemEntities(activityDTO);
            String stockError = validateItemsStock(items);
            if (stockError != null) return R.fail(stockError);
            seckillService.createActivity(activity, items);
            log.info("秒杀活动创建成功: id={}", activity.getId());
            return R.ok(activity.getId());
        } catch (Exception e) {
            log.error("秒杀活动创建失败: {}", e.getMessage(), e);
            return R.fail("创建秒杀活动失败: " + e.getMessage());
        }
    }

    @Override
    @PutMapping("/seckill/{activityId}")
    public R<Boolean> updateSeckillActivity(@PathVariable("activityId") Long activityId, @RequestBody SeckillActivityDTO activityDTO) {
        try {
            SeckillActivity activity = seckillActivityMapper.selectById(activityId);
            if (activity == null) return R.fail("秒杀活动不存在");
            activity.setName(activityDTO.getName());
            activity.setStartTime(activityDTO.getStartTime());
            activity.setEndTime(activityDTO.getEndTime());
            List<SeckillActivityItem> items = toItemEntities(activityDTO);
            String stockError = validateItemsStock(items);
            if (stockError != null) return R.fail(stockError);
            seckillService.updateActivity(activity, items);
            return R.ok(true);
        } catch (Exception e) {
            log.error("秒杀活动更新失败: {}", e.getMessage(), e);
            return R.fail("更新秒杀活动失败: " + e.getMessage());
        }
    }

    @Override
    @DeleteMapping("/seckill/{activityId}")
    public R<Boolean> deleteSeckillActivity(@PathVariable("activityId") Long activityId) {
        seckillService.deleteActivity(activityId);
        return R.ok(true);
    }

    @Override
    @GetMapping("/seckill/list/detail")
    public R<List<SeckillActivityDetailDTO>> listSeckillActivitiesWithProduct() {
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
            // 根据时间动态计算状态
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

    /**
     * 活动实体 + 商品条目 → DTO
     */
    private SeckillActivityDTO toActivityDTO(SeckillActivity activity, List<SeckillActivityItem> items) {
        SeckillActivityDTO dto = new SeckillActivityDTO();
        dto.setId(activity.getId());
        dto.setName(activity.getName());
        dto.setStartTime(activity.getStartTime());
        dto.setEndTime(activity.getEndTime());
        dto.setStatus(activity.getStatus());
        dto.setItems(items.stream().map(item -> {
            SeckillItemDTO itemDTO = new SeckillItemDTO();
            BeanUtils.copyProperties(item, itemDTO);
            return itemDTO;
        }).collect(Collectors.toList()));
        return dto;
    }

    /**
     * DTO 商品条目 → 实体列表
     */
    private List<SeckillActivityItem> toItemEntities(SeckillActivityDTO activityDTO) {
        if (activityDTO.getItems() == null) return Collections.emptyList();
        return activityDTO.getItems().stream().map(itemDTO -> {
            SeckillActivityItem item = new SeckillActivityItem();
            BeanUtils.copyProperties(itemDTO, item);
            return item;
        }).collect(Collectors.toList());
    }

    /**
     * 校验秒杀商品库存：商品与 SKU 必须存在、SKU 有库存、秒杀库存不超过 SKU 实际库存。
     * 校验通过返回 null，否则返回错误信息。
     */
    private String validateItemsStock(List<SeckillActivityItem> items) {
        if (items == null || items.isEmpty()) return null;
        Map<Long, ProductDTO> productCache = new HashMap<>();
        for (int i = 0; i < items.size(); i++) {
            SeckillActivityItem item = items.get(i);
            String prefix = "第 " + (i + 1) + " 个商品：";
            if (item.getProductId() == null || item.getSkuId() == null) {
                return prefix + "商品或规格不能为空";
            }
            ProductDTO product = productCache.get(item.getProductId());
            if (product == null && !productCache.containsKey(item.getProductId())) {
                R<ProductDTO> result = productFeignClient.getProductById(item.getProductId());
                product = result != null && result.getData() != null ? result.getData() : null;
                productCache.put(item.getProductId(), product);
            }
            if (product == null) {
                return prefix + "商品不存在";
            }
            SkuDTO sku = product.getSkus() == null ? null : product.getSkus().stream()
                    .filter(s -> s.getId() != null && s.getId().equals(item.getSkuId()))
                    .findFirst().orElse(null);
            if (sku == null) {
                return prefix + "所选规格不存在";
            }
            int skuStock = sku.getStock() == null ? 0 : sku.getStock();
            if (skuStock <= 0) {
                return prefix + "「" + product.getName() + " " + sku.getSkuName() + "」已无库存，无法参加活动";
            }
            int seckillStock = item.getTotalStock() == null ? 0 : item.getTotalStock();
            if (seckillStock > skuStock) {
                return prefix + "秒杀库存不能超过实际库存（可用 " + skuStock + " 件）";
            }
        }
        return null;
    }
}
