package com.byw.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byw.cart.entity.CartItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {

    /** 查询购物车项（包含已逻辑删除的记录，绕过 @TableLogic） */
    @Select("SELECT * FROM t_cart_item WHERE user_id = #{userId} AND sku_id = #{skuId} LIMIT 1")
    CartItem selectByUserAndSkuIncludeDeleted(Long userId, Long skuId);

    /** 恢复已软删除的购物车项并更新信息 */
    @Update("UPDATE t_cart_item SET deleted = 0, quantity = #{quantity}, price = #{price}, " +
            "sku_name = #{skuName}, product_name = #{productName}, spec_data = #{specData}, " +
            "product_image = #{productImage}, product_id = #{productId}, " +
            "selected = 1, updated_at = NOW() " +
            "WHERE id = #{id}")
    int restoreById(CartItem item);

    /** 硬删除记录（绕过 @TableLogic） */
    @Delete("DELETE FROM t_cart_item WHERE id = #{id}")
    int hardDeleteById(@Param("id") Long id);
}
