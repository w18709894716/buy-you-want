-- =====================================================================
-- 增量迁移：退款幂等业务键 + 订单业务幂等键
-- 日期：2026-10-13
-- 适用：按 docs/database/*.sql 初始化过的存量库；全新部署直接执行全量脚本即可（已含本变更）
-- 前置：无
--
-- 变更内容
--   1) byw_pay.t_refund_record 增加 ref_no（退款幂等业务键）+ 唯一索引 uk_ref_no
--      目的：同一售后单重复驱动退款时，支付侧据 refNo 幂等返回，不再重复退款。
--   2) byw_order.t_order 增加 biz_key（业务幂等键）+ 唯一索引 uk_biz_key
--      目的：秒杀订单消息重复投递时，唯一索引兜底，保证只生成一单。
--
-- 回滚（按倒序执行）
--   USE byw_order; ALTER TABLE t_order DROP INDEX uk_biz_key, DROP COLUMN biz_key;
--   USE byw_pay;   ALTER TABLE t_refund_record DROP INDEX uk_ref_no, DROP COLUMN ref_no;
--
-- 并发/锁影响：t_order 数据量大时加唯一索引会短暂持锁，建议低峰执行。
-- 幂等性：MySQL 的 ADD COLUMN/ADD INDEX 不支持 IF NOT EXISTS，
--         重复执行会因"列/索引已存在"报错，属预期，可忽略该错误继续。
-- =====================================================================

USE byw_pay;
ALTER TABLE t_refund_record
    ADD COLUMN ref_no VARCHAR(64) NULL COMMENT '退款幂等业务键(售后单号等)，重复请求据此幂等' AFTER reason,
    ADD UNIQUE KEY uk_ref_no (ref_no);

USE byw_order;
ALTER TABLE t_order
    ADD COLUMN biz_key VARCHAR(64) NULL COMMENT '业务幂等键(如秒杀:活动:条目:用户)，NULL=普通下单' AFTER parent_order_no,
    ADD UNIQUE KEY uk_biz_key (biz_key);
