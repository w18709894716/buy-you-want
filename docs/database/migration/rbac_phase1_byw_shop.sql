-- ============================================================
-- RBAC 权限体系一期 · 增量迁移脚本（byw_shop 库）
-- 用途：在【保留存量店铺/商家账号数据】前提下，为商家子账号（员工）体系加 parent_id
-- 特性：可重复执行（幂等）——列变更用 information_schema 守卫
-- ⚠ 执行前务必备份：  mysqldump -u root -p byw_shop > byw_shop_backup.sql
-- ============================================================
USE byw_shop;

-- ------------------------------------------------------------
-- t_merchant_account 新增 parent_id 列（NULL=主账号，非NULL=子账号/员工）
-- 仅当列不存在时执行，重复运行安全
-- ------------------------------------------------------------
SET @has_parent := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'byw_shop' AND TABLE_NAME = 't_merchant_account' AND COLUMN_NAME = 'parent_id');

SET @sql := IF(@has_parent = 0,
  'ALTER TABLE t_merchant_account ADD COLUMN parent_id BIGINT NULL COMMENT ''主账号ID，NULL=主账号，非NULL=子账号(员工)'' AFTER shop_id',
  'SELECT ''[skip] t_merchant_account.parent_id 已存在'' AS msg');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 说明：存量商家账号（demo_merchant / official_shop 等）均为主账号，parent_id 保持 NULL 即可，无需回填。

-- ============================================================
-- 迁移完成。校验：
--   SHOW COLUMNS FROM t_merchant_account LIKE 'parent_id'; -- 应存在
--   SELECT id, username, parent_id FROM t_merchant_account; -- 存量账号 parent_id 均为 NULL
-- ============================================================
