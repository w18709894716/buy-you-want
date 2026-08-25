package com.byw.product.es;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * 商品 ES 文档（索引 byw-product）。
 * 仅保留搜索/筛选/排序维度，展示数据仍按命中 id 回 MySQL 取，保证返回与现接口一致且实时。
 */
@Data
@Document(indexName = ProductDoc.INDEX, createIndex = false)
public class ProductDoc {

    public static final String INDEX = "byw-product";

    @Id
    private Long id;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Text)
    private String subtitle;

    @Field(type = FieldType.Long)
    private Long shopId;

    @Field(type = FieldType.Long)
    private Long categoryId;

    @Field(type = FieldType.Integer)
    private Integer salesCount;

    @Field(type = FieldType.Integer)
    private Integer status;

    @Field(type = FieldType.Integer)
    private Integer auditStatus;

    /** 创建时间（epoch 毫秒，用于新品排序） */
    @Field(type = FieldType.Long)
    private Long createdAt;
}
