package com.byw.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.byw.product.entity.Product;
import com.byw.product.entity.ProductWithPrice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 带 SKU 最低价的分页查询：聚合子查询 JOIN + DB 层筛选/排序/分页，
     * 用于价格区间筛选/价格排序场景，避免全量商品加载进内存。
     * orderBy 仅由服务端白名单拼接，不接受用户输入。
     */
    @Select("<script>" +
            "SELECT p.*, m.min_price " +
            "FROM t_product p " +
            "LEFT JOIN (SELECT product_id, MIN(price) AS min_price FROM t_sku WHERE deleted = 0 GROUP BY product_id) m " +
            "ON m.product_id = p.id " +
            "WHERE p.deleted = 0 AND p.status = 1 AND p.audit_status = 1 " +
            "<if test='shopId != null'> AND p.shop_id = #{shopId} </if>" +
            "<if test='brandId != null'> AND p.brand_id = #{brandId} </if>" +
            "<if test='categoryIds != null and categoryIds.size() > 0'> AND p.category_id IN " +
            "<foreach collection='categoryIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach> </if>" +
            "<if test='productIds != null and productIds.size() > 0'> AND p.id IN " +
            "<foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach> </if>" +
            "<if test='keyword != null'> AND (p.name LIKE CONCAT('%', #{keyword}, '%') OR p.subtitle LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "<if test='minPrice != null'> AND m.min_price &gt;= #{minPrice} </if>" +
            "<if test='maxPrice != null'> AND m.min_price &lt;= #{maxPrice} </if>" +
            "ORDER BY ${orderBy}" +
            "</script>")
    IPage<ProductWithPrice> selectPageWithMinPrice(IPage<ProductWithPrice> page,
                                                   @Param("shopId") Long shopId,
                                                   @Param("brandId") Long brandId,
                                                   @Param("categoryIds") List<Long> categoryIds,
                                                   @Param("productIds") List<Long> productIds,
                                                   @Param("keyword") String keyword,
                                                   @Param("minPrice") BigDecimal minPrice,
                                                   @Param("maxPrice") BigDecimal maxPrice,
                                                   @Param("orderBy") String orderBy);
}
