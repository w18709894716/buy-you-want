-- =============================================================================
-- 迁移脚本：补齐基线漏建的 3 张表（IM 技能组 x2、拼团活动）
-- 日期：2026-10-21
--
-- 背景：全项目复审发现 48 个 MyBatis-Plus 实体对 46 张表，列级零错配，但有 3 张表
--       在 docs/database 基线里根本不存在，用基线脚本建出来的环境一调相关接口就报
--       「Table doesn't exist」：
--         · byw_im.t_im_skill_group / t_im_skill_group_staff
--           —— SkillGroup / SkillGroupStaff 实体 + ImSkillGroupController(/im/skill-group)
--              仍在线，商家端 views/skill-group/index.vue 也在调这套接口。
--              注意：该机制已被 t_im_dispatch_group（分流分组）取代，此处只补 schema
--              让存量代码不再 500，是否连同代码一起下线属业务决策。
--         · byw_promotion.t_group_buy_activity
--           —— GroupBuyActivity 实体 + GroupBuyController(/group-buy) 在线。
--
-- 执行前必读：本脚本只做**新增建表**，不改动、不删除任何既有数据。
-- 幂等性：统一用 CREATE TABLE IF NOT EXISTS，可重复执行。
-- 字段依据：与实体类逐字段对齐（含 @TableLogic 的 deleted、@TableField(fill) 的时间列）。
-- =============================================================================

-- ① IM 技能组（旧版分组机制，已被分流分组取代）
USE byw_im;

CREATE TABLE IF NOT EXISTS t_im_skill_group (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  shop_id BIGINT NOT NULL COMMENT '店铺ID',
  group_name VARCHAR(50) NOT NULL COMMENT '技能组名称（售前/售后/物流…）',
  keywords VARCHAR(500) NULL COMMENT '路由关键词（逗号分隔，匹配用户消息首句）',
  sort INT NOT NULL DEFAULT 0 COMMENT '优先级（数字越小越优先匹配）',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1启用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_shop_id (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM 技能组（旧版，已被分流分组取代）';

CREATE TABLE IF NOT EXISTS t_im_skill_group_staff (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  group_id BIGINT NOT NULL COMMENT '技能组ID',
  staff_id BIGINT NOT NULL COMMENT '客服ID（merchant_account.id）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_staff (group_id, staff_id),
  KEY idx_staff_id (staff_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM 技能组-客服关联（旧版）';

-- ② 拼团活动
USE byw_promotion;

CREATE TABLE IF NOT EXISTS t_group_buy_activity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT '活动名称',
    product_id BIGINT NOT NULL COMMENT '商品ID（SPU）',
    group_price DECIMAL(10,2) NOT NULL COMMENT '拼团价',
    original_price DECIMAL(10,2) NOT NULL COMMENT '原价',
    group_size INT NOT NULL DEFAULT 2 COMMENT '成团人数',
    max_groups INT NULL COMMENT '最大成团数（空=不限）',
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status TINYINT DEFAULT 0 COMMENT '0未开始 1进行中 2已结束',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_status_end_time (status, end_time),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼团活动';

-- ③ 验证：三张表都在
SELECT TABLE_SCHEMA, TABLE_NAME FROM information_schema.TABLES
 WHERE TABLE_NAME IN ('t_im_skill_group', 't_im_skill_group_staff', 't_group_buy_activity')
 ORDER BY TABLE_SCHEMA, TABLE_NAME;

-- =============================================================================
-- 回滚
-- =============================================================================
-- USE byw_im;
-- DROP TABLE IF EXISTS t_im_skill_group_staff;
-- DROP TABLE IF EXISTS t_im_skill_group;
-- USE byw_promotion;
-- DROP TABLE IF EXISTS t_group_buy_activity;
