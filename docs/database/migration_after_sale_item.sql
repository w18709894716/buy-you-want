-- 售后商品级改造迁移脚本
-- t_after_sale 增加商品明细关联及商品快照字段：
--   order_item_id 为 NULL 表示历史「订单级」售后（整单），保持兼容；新申请一律商品级
USE byw_order;

ALTER TABLE t_after_sale
    ADD COLUMN order_item_id BIGINT NULL COMMENT '关联订单明细ID(NULL=历史订单级售后)' AFTER order_no,
    ADD COLUMN product_name VARCHAR(200) NULL COMMENT '商品名称快照' AFTER order_item_id,
    ADD COLUMN sku_name VARCHAR(200) NULL COMMENT 'SKU规格快照' AFTER product_name,
    ADD COLUMN product_image VARCHAR(500) NULL COMMENT '商品图片快照' AFTER sku_name,
    ADD INDEX idx_order_item_id (order_item_id);

-- 存量回填：单商品订单的售后单可确定唯一明细，直接回填明细ID与商品快照
UPDATE t_after_sale a
JOIN (
    SELECT order_no, MIN(id) AS item_id, COUNT(*) AS item_count
    FROM t_order_item
    GROUP BY order_no
) i ON i.order_no = a.order_no AND i.item_count = 1
JOIN t_order_item oi ON oi.id = i.item_id
SET a.order_item_id = oi.id,
    a.product_name  = oi.product_name,
    a.sku_name      = oi.sku_name,
    a.product_image = oi.product_image
WHERE a.order_item_id IS NULL;
