package com.byw.user.service;

import com.byw.common.core.result.PageResult;

import java.util.List;
import java.util.Map;

public interface UserFavoriteService {

    /** 添加收藏（幂等） */
    void addFavorite(Long userId, Long productId);

    /** 取消收藏 */
    void removeFavorite(Long userId, Long productId);

    /** 是否已收藏 */
    boolean isFavorite(Long userId, Long productId);

    /** 当前用户已收藏的商品ID列表（用于前端标记收藏态） */
    List<Long> getFavoriteProductIds(Long userId);

    /** 分页获取收藏商品（含商品名称/图片/价格富化） */
    PageResult<Map<String, Object>> getFavorites(Long userId, Integer pageNum, Integer pageSize);
}
