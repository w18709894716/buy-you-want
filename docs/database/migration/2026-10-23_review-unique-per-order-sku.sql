-- =============================================================================
-- 迁移脚本：同一订单的同一商品只允许一条有效评价
-- 日期：2026-10-23
-- 对应复审条目：P1-E-5（t_review 缺 (order_no, sku_id) 唯一键，重复评价只靠应用层校验）
--
-- 背景：第四批复审把"评价可伪造 / 可重复提交"的应用层校验补齐了
--       （ReviewServiceImpl.createReview / createBatchReviews 先校验归属、状态、sku 归属，
--       再查重、再校验同批次 skuId 不重复），但那两步"先查再插"在并发下没有原子性：
--       两个请求可以同时通过查重，然后各插一条。唯一键是这条规则的最后一道兜底 ——
--       它不依赖代码纪律，也不会被后续重构改坏。
--
-- 为什么不是简单的 UNIQUE(order_no, sku_id)：
--   t_review 是逻辑删除（deleted 标记）。若直接对两列建唯一键，管理员删掉一条评价后，
--   它的行仍然占着这个键，用户重新评价会被数据库拒绝 —— 应用层查重会因为 @TableLogic
--   过滤掉已删除行而认为"没评过"，最后变成一个 500。
--   这里沿用 t_after_sale.active_flag 的做法：加一个"未删除=1、已删除=NULL"的虚拟生成列，
--   唯一索引不比较 NULL，于是已删除的行不参与约束，未删除的行之间仍然唯一。
--
-- 执行前必读：
--   1. 先执行 ① 预览存量重复规模；有输出说明历史上已经产生过重复评价；
--   2. ② 会把重复行**软删除**（deleted=1）而不是物理删除，保留内容可供追溯；
--      执行前请备份：mysqldump -uroot -p byw_review t_review > bak_t_review.sql；
--   3. 本脚本只能执行一次（① 的 ADD COLUMN 不是幂等的）。
--
-- 兼容性：MySQL 5.7+（虚拟生成列 + 生成列上的二级索引）。
-- 新环境不需要本脚本：docs/database/byw_review.sql 已经内置该列与唯一键。
-- =============================================================================

USE byw_review;

-- ① 预览：未删除的重复 (order_no, sku_id)（期望结果为空）
SELECT order_no, sku_id, COUNT(*) AS dup_count
  FROM t_review
 WHERE COALESCE(deleted, 0) = 0
 GROUP BY order_no, sku_id
HAVING dup_count > 1;

-- ② 加生成列（未删除=1，已删除=NULL）
ALTER TABLE t_review
    ADD COLUMN active_uk TINYINT
        GENERATED ALWAYS AS (CASE WHEN COALESCE(deleted, 0) = 0 THEN 1 ELSE NULL END) VIRTUAL
        COMMENT '唯一键辅助列：未删除=1，已删除=NULL';

-- ③ 消解：每组 (order_no, sku_id) 只保留 id 最小的一条，其余标记为已删除。
--    重复行本身就是并发缺陷产生的脏数据；保留最早的一条，内容不会丢。
UPDATE t_review r
  JOIN (
        SELECT order_no,
               sku_id,
               MIN(id) AS keep_id
          FROM t_review
         WHERE COALESCE(deleted, 0) = 0
         GROUP BY order_no, sku_id
        HAVING COUNT(*) > 1
       ) k
    ON r.order_no = k.order_no
   AND r.sku_id = k.sku_id
   SET r.deleted = 1
 WHERE COALESCE(r.deleted, 0) = 0
   AND r.id <> k.keep_id;

-- ④ 复核：应返回 0 行
SELECT order_no, sku_id, COUNT(*) AS dup_count
  FROM t_review
 WHERE COALESCE(deleted, 0) = 0
 GROUP BY order_no, sku_id
HAVING dup_count > 1;

-- ⑤ 加唯一键（③ 之后未删除行已无重复，此处不会失败）
ALTER TABLE t_review
    ADD UNIQUE KEY uk_order_sku_active (order_no, sku_id, active_uk);

-- ⑥ 验证：应返回 1
--    information_schema.STATISTICS 是"每个索引列一行"，不是"每个索引一行"，
--    直接 COUNT(*) 对这个三列索引会返回 3。这里按 INDEX_NAME 去重统计，
--    并限定 NON_UNIQUE = 0（确认是真唯一键，而不是同名普通索引）。
--    想核对列顺序与可空性，再跑：
--      SHOW INDEX FROM t_review WHERE Key_name = 'uk_order_sku_active';
--    期望 KEY 顺序 order_no → sku_id → active_uk，三列 Non_unique 均为 0。
SELECT COUNT(DISTINCT INDEX_NAME) AS uk_order_sku_active_exists
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 't_review'
   AND NON_UNIQUE = 0
   AND INDEX_NAME = 'uk_order_sku_active';

-- =============================================================================
-- 回滚
-- =============================================================================
-- ALTER TABLE t_review DROP INDEX uk_order_sku_active, DROP COLUMN active_uk;
--
-- 注意：③ 软删除的重复评价**不会**随回滚恢复（deleted 已置 1），
--       如需还原请使用执行前的备份。
