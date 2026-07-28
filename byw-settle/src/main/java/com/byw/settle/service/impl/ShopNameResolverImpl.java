package com.byw.settle.service.impl;

import com.byw.api.shop.ShopFeignClient;
import com.byw.api.shop.dto.ShopDTO;
import com.byw.common.core.result.R;
import com.byw.settle.service.ShopNameResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopNameResolverImpl implements ShopNameResolver {

    private final ShopFeignClient shopFeignClient;

    @Override
    public Map<Long, String> resolve(List<Long> shopIds) {
        if (shopIds == null || shopIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> distinct = shopIds.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            R<List<ShopDTO>> resp = shopFeignClient.getShopsByIds(distinct);
            if (resp != null && resp.isSuccess() && resp.getData() != null) {
                Map<Long, String> map = new HashMap<>();
                for (ShopDTO shop : resp.getData()) {
                    map.put(shop.getId(), shop.getName());
                }
                return map;
            }
        } catch (Exception e) {
            log.warn("批量获取店铺名失败: {}", distinct, e);
        }
        return Collections.emptyMap();
    }
}
