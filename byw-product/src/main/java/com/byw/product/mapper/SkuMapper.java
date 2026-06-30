package com.byw.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byw.product.entity.Sku;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SkuMapper extends BaseMapper<Sku> {

    /** 查询包含软删除的 SKU 记录 */
    @Select("SELECT * FROM t_sku WHERE product_id = #{productId} AND deleted = 1")
    List<Sku> selectDeletedByProductId(@Param("productId") Long productId);

    /** 按 ID 物理删除（绕过软删除） */
    @Delete("DELETE FROM t_sku WHERE id = #{id}")
    int hardDeleteById(@Param("id") Long id);
}
