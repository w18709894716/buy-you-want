package com.byw.settle.service;

import java.util.List;
import java.util.Map;

/**
 * 店铺名解析辅助：批量把 shopId 映射为店铺名（供结算单/提现单列表回填展示）。
 */
public interface ShopNameResolver {

    Map<Long, String> resolve(List<Long> shopIds);
}
