-- ============================================================
-- IM 多客服支持（介入）· 增量迁移脚本（byw_im 库）
-- 用途：t_conversation 新增 joiners 字段（介入客服ID集合，JSON数组如 "[3,5]"）
-- 特性：可重复执行（幂等）——列变更用 information_schema 守卫
-- ⚠ 执行前务必备份：mysqldump -u root -p byw_im > byw_im_backup.sql
-- ============================================================
USE byw_im;

-- ------------------------------------------------------------
-- 1. t_conversation：新增介入客服字段
-- ------------------------------------------------------------
SET @db = 'byw_im';
SET @table = 't_conversation';

-- 1.1 joiners
SET @col = 'joiners';
SET @exists = (SELECT COUNT(1) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @table AND COLUMN_NAME = @col);
SET @sql = IF(@exists = 0,
    'ALTER TABLE t_conversation ADD COLUMN `joiners` VARCHAR(255) NULL COMMENT ''介入客服ID集合（JSON数组）''',
    'SELECT ''joiners 已存在，跳过''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
