-- =============================================================================
-- 迁移脚本：领券防重 + 售后"进行中"唯一
-- 日期：2026-10-14
-- 对应复审条目：P0-3（t_coupon_record 缺 (coupon_id, user_id) 唯一键）
--               P0-4（t_after_sale 缺"同一明细只能有一条进行中售后"约束）
--
-- 背景：这两处原来都是"先查再插"，并发下两个请求会同时通过检查，
--       因此必须由数据库唯一键兜底 —— 它不依赖代码纪律，也不会被后续重构改坏。
--
-- 执行前必读：
--   1. 先执行 ① 与 ⑤ 两条预览，确认存量重复数据的规模；若都是 0 行，可直接执行 ③ / ⑦；
--   2. 脚本会删除重复的领券记录、并把多余的"进行中"售后单置为已撤销(4)；
--      **执行前请备份两张表**（例如：mysqldump -uroot -p byw_promotion t_coupon_record > bak.sql）；
--   3. 存量重复数据会让唯一键创建失败，所以消解必须放在 ALTER 之前。
--
-- 兼容性：面向 MySQL 5.7+（未使用 ROW_NUMBER 等 8.0 窗口函数）。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 一、t_coupon_record：一个用户对同一张券只允许一条记录
-- -----------------------------------------------------------------------------

-- ① 预览：存在重复领取的 (coupon_id, user_id)（期望结果为空）
SELECT coupon_id, user_id, COUNT(*) AS dup_count
  FROM t_coupon_record
 GROUP BY coupon_id, user_id
HAVING dup_count > 1;

-- ② 消解：同一 (coupon_id, user_id) 只保留"最有用"的一条
--    优先级：已使用(1) > 未使用(0) > 已过期(2)，同优先级保留最早的一条，其余删除。
--    （重复行本身就是并发缺陷产生的脏数据，删除未使用/已过期的重复项不会造成资损）
DELETE r
  FROM t_coupon_record r
  JOIN (
        SELECT coupon_id,
               user_id,
               SUBSTRING_INDEX(GROUP_CONCAT(id ORDER BY FIELD(status, 1, 0, 2), id), ',', 1) AS keep_id
          FROM t_coupon_record
         GROUP BY coupon_id, user_id
        HAVING COUNT(*) > 1
       ) k
    ON r.coupon_id = k.coupon_id
   AND r.user_id = k.user_id
 WHERE r.id <> k.keep_id;

-- ③ 加唯一键（存量重复已被②清理，此处不会失败）
ALTER TABLE t_coupon_record
    ADD UNIQUE KEY uk_coupon_user (coupon_id, user_id);

-- ④ 验证：应返回 1
SELECT COUNT(*) AS uk_coupon_user_exists
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 't_coupon_record'
   AND INDEX_NAME = 'uk_coupon_user';

-- -----------------------------------------------------------------------------
-- 二、t_after_sale：同一订单明细同时只能有一条"进行中"售后单（状态 0/1/5/6）
-- -----------------------------------------------------------------------------

-- ⑤ 预览：存在多条"进行中"售后的订单明细（期望结果为空）
SELECT order_item_id, COUNT(*) AS active_count
  FROM t_after_sale
 WHERE deleted = 0
   AND status IN (0, 1, 5, 6)
   AND order_item_id IS NOT NULL
 GROUP BY order_item_id
HAVING active_count > 1;

-- ⑥ 消解：保留推进最靠后的一条（退款中6 > 待商家收货5 > 待买家寄回1 > 待审核0），
--    同优先级保留最早的一条，其余置为"已撤销(4)"。
--    ⚠️ 若⑤有输出，请先人工确认这些单是否已有在途退款：置为已撤销后它们不再被退款对账任务扫描。
UPDATE t_after_sale a
  JOIN (
        SELECT order_item_id,
               SUBSTRING_INDEX(GROUP_CONCAT(id ORDER BY FIELD(status, 6, 5, 1, 0), id), ',', 1) AS keep_id
          FROM t_after_sale
         WHERE deleted = 0
           AND status IN (0, 1, 5, 6)
           AND order_item_id IS NOT NULL
         GROUP BY order_item_id
        HAVING COUNT(*) > 1
       ) k
    ON a.order_item_id = k.order_item_id
 WHERE a.deleted = 0
   AND a.status IN (0, 1, 5, 6)
   AND a.id <> k.keep_id;

-- ⑦ 复核：⑥ 之后应返回 0 行
SELECT order_item_id, COUNT(*) AS active_count
  FROM t_after_sale
 WHERE deleted = 0
   AND status IN (0, 1, 5, 6)
   AND order_item_id IS NOT NULL
 GROUP BY order_item_id
HAVING active_count > 1;

-- ⑧ 加生成列 + 唯一键
--    MySQL 没有部分唯一索引，用"进行中=1、其余=NULL"的虚拟生成列实现；
--    唯一索引不约束 NULL，所以已拒绝/已撤销/已完成/逻辑删除的行仍可有多条。
ALTER TABLE t_after_sale
    ADD COLUMN active_flag TINYINT
        GENERATED ALWAYS AS (CASE WHEN deleted = 0 AND status IN (0, 1, 5, 6) THEN 1 ELSE NULL END) VIRTUAL,
    ADD UNIQUE KEY uk_item_active (order_item_id, active_flag);

-- ⑨ 验证：进行中的行 active_flag=1，其余为 NULL
SELECT status, active_flag, COUNT(*) AS cnt
  FROM t_after_sale
 GROUP BY status, active_flag
 ORDER BY status;

-- =============================================================================
-- 回滚
-- =============================================================================
-- ALTER TABLE t_after_sale DROP INDEX uk_item_active, DROP COLUMN active_flag;
-- ALTER TABLE t_coupon_record DROP INDEX uk_coupon_user;
--
-- 注意：② 删除的领券记录、⑥ 置为"已撤销"的售后单**不会**随回滚恢复，
--       如需还原请使用执行前的备份。
