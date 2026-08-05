-- ============================================================
-- IM 多客服支持 · 增量迁移脚本（byw_im 库）
-- 用途：t_conversation 新增 assignee_id / assignee_name 字段
-- 特性：可重复执行（幂等）——列变更用 information_schema 守卫
-- ⚠ 执行前务必备份：mysqldump -u root -p byw_im > byw_im_backup.sql
-- ============================================================
USE byw_im;

-- ------------------------------------------------------------
-- 1. t_conversation：新增接待客服字段
-- ------------------------------------------------------------
SET @db = 'byw_im';
SET @table = 't_conversation';

-- 1.1 assignee_id
SET @col = 'assignee_id';
SET @exists = (SELECT COUNT(1) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @table AND COLUMN_NAME = @col);
SET @sql = IF(@exists = 0,
    'ALTER TABLE t_conversation ADD COLUMN `assignee_id` BIGINT NULL COMMENT ''当前接待客服ID（merchant_account.id）''',
    'SELECT ''assignee_id 已存在，跳过''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1.2 assignee_name
SET @col = 'assignee_name';
SET @exists = (SELECT COUNT(1) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @table AND COLUMN_NAME = @col);
SET @sql = IF(@exists = 0,
    'ALTER TABLE t_conversation ADD COLUMN `assignee_name` VARCHAR(50) NULL COMMENT ''接待客服姓名''',
    'SELECT ''assignee_name 已存在，跳过''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;