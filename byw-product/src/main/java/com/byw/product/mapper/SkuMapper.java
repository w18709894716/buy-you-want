package com.byw.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byw.product.entity.Sku;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SkuMapper extends BaseMapper<Sku> {

    /** 查询包含软删除的 SKU 记录 */
    @Select("SELECT * FROM t_sku WHERE product_id = #{productId} AND deleted = 1")
    List<Sku> selectDeletedByProductId(@Param("productId") Long productId);

    /** 按 ID 物理删除（绕过软删除） */
    @Delete("DELETE FROM t_sku WHERE id = #{id}")
    int hardDeleteById(@Param("id") Long id);

    /**
     * 按增量调整库存（商家编辑商品时使用），delta 可为负。
     *
     * <p>用 {@code stock = stock + delta} 而非整值覆盖：编辑页读到的是打开页面那一刻的快照，
     * 期间的下单预占/售后回补都发生在数据库侧，整值覆盖会把这些变更抹掉，凭空多出库存导致超卖。
     *
     * <p>WHERE 带 {@code stock + delta >= 0}：调整后的可售库存不能为负，
     * 影响行数为 0 即说明期间已被订单占用，由调用方显式失败提示刷新。
     */
    @Update("UPDATE t_sku SET stock = stock + #{delta} WHERE id = #{id} AND deleted = 0 AND stock + #{delta} >= 0")
    int adjustStock(@Param("id") Long id, @Param("delta") Integer delta);
}
