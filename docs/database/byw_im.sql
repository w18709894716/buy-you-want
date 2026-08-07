CREATE DATABASE IF NOT EXISTS byw_im DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE byw_im;

-- 客服会话表（会话关系存 MySQL，消息流存 MongoDB im_messages 集合）
DROP TABLE IF EXISTS t_conversation;
CREATE TABLE t_conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '买家用户ID',
    shop_id BIGINT NOT NULL COMMENT '归属店铺ID(多租户维度)',
    last_message VARCHAR(500) COMMENT '最后一条消息摘要',
    last_message_type VARCHAR(20) COMMENT '最后一条消息类型 text/image/product_card/order_card',
    last_message_time DATETIME COMMENT '最后一条消息时间',
    user_unread INT NOT NULL DEFAULT 0 COMMENT '买家未读数',
    shop_unread INT NOT NULL DEFAULT 0 COMMENT '商家未读数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    assignee_id BIGINT NULL COMMENT '当前接待客服ID（merchant_account.id）',
    assignee_name VARCHAR(50) NULL COMMENT '接待客服姓名',
    joiners VARCHAR(255) NULL COMMENT '介入客服ID集合（JSON数组）',
    skill_group_id BIGINT NULL COMMENT '路由到的技能组ID',
    UNIQUE KEY uk_user_shop (user_id, shop_id, deleted),
    INDEX idx_user_id (user_id),
    INDEX idx_shop_id (shop_id),
    INDEX idx_last_message_time (last_message_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服会话表';

-- ========== 说明 ==========
-- 消息流存 MongoDB（库 byw_im，集合 im_messages），无需在此建表。
-- 文档结构见 com.byw.im.document.ImMessage：
--   { conversationId, senderId, senderRole, shopId, userId, type, content, extra, read, createdAt }

-- ============================================================
-- IM 满意度评价表
-- ============================================================
DROP TABLE IF EXISTS t_im_satisfaction;
CREATE TABLE t_im_satisfaction (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  shop_id BIGINT NOT NULL COMMENT '店铺ID',
  conversation_id BIGINT NOT NULL COMMENT '会话ID（唯一）',
  user_id BIGINT NOT NULL COMMENT '评价用户ID',
  staff_id BIGINT NULL COMMENT '接待客服ID（评价对象）',
  staff_name VARCHAR(50) NULL COMMENT '客服姓名（评价时快照）',
  rating TINYINT NOT NULL COMMENT '评分 1-5',
  tags VARCHAR(200) NULL COMMENT '评价标签（逗号分隔）',
  comment VARCHAR(500) NULL COMMENT '留言',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
  PRIMARY KEY (id),
  UNIQUE KEY uk_conversation (conversation_id),
  KEY idx_shop_id (shop_id),
  KEY idx_staff_id (staff_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM 满意度评价';

-- ============================================================
-- IM 服务记录表（一次服务 = 一次评价单元，取代 t_im_satisfaction）
-- 服务开始：客服接入 / 上次服务结束后再次发消息；
-- 服务结束：双方 10 分钟无消息超时自动结束（提前 3 分钟提示）；
-- 客服掉线不算结束，会话重新分配后由新客服继续服务，评价对象始终为最终处理人。
-- ============================================================
DROP TABLE IF EXISTS t_im_service_record;
CREATE TABLE t_im_service_record (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  conversation_id BIGINT NOT NULL COMMENT '关联会话ID',
  shop_id BIGINT NOT NULL COMMENT '店铺ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS-进行中 ENDED-已结束 RATED-已评价',
  staff_id BIGINT NULL COMMENT '最终处理客服ID（评价对象；转接/接管时更新，介入不更新）',
  staff_name VARCHAR(50) NULL COMMENT '客服姓名快照',
  started_at DATETIME NOT NULL COMMENT '服务开始时间',
  ended_at DATETIME NULL COMMENT '服务结束时间',
  end_reason VARCHAR(30) NULL COMMENT '结束原因：TIMEOUT-超时自动结束',
  last_message_time DATETIME NULL COMMENT '最后一条消息时间（超时检测基准）',
  notified_before_end TINYINT NOT NULL DEFAULT 0 COMMENT '是否已发送提前结束通知',
  rating TINYINT NULL COMMENT '评分 1-5',
  tags VARCHAR(200) NULL COMMENT '评价标签（逗号分隔）',
  comment VARCHAR(500) NULL COMMENT '评价留言',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
  PRIMARY KEY (id),
  KEY idx_conversation (conversation_id),
  KEY idx_status (status),
  KEY idx_shop_id (shop_id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM 服务记录（一次服务=一次评价单元）';

-- ============================================================
-- IM 技能组表
-- ============================================================
DROP TABLE IF EXISTS t_im_skill_group;
CREATE TABLE t_im_skill_group (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM 技能组';

-- ============================================================
-- IM 技能组-客服关联表
-- ============================================================
DROP TABLE IF EXISTS t_im_skill_group_staff;
CREATE TABLE t_im_skill_group_staff (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  group_id BIGINT NOT NULL COMMENT '技能组ID',
  staff_id BIGINT NOT NULL COMMENT '客服ID（merchant_account.id）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_group_staff (group_id, staff_id),
  KEY idx_staff_id (staff_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM 技能组-客服关联';

-- ============================================================
-- IM FAQ 知识库表
-- ============================================================
DROP TABLE IF EXISTS t_im_faq;
CREATE TABLE t_im_faq (
  id BIGINT NOT NULL AUTO_INCREMENT,
  shop_id BIGINT NOT NULL COMMENT '店铺ID',
  question VARCHAR(500) NOT NULL COMMENT '问题',
  answer VARCHAR(2000) NOT NULL COMMENT '答案',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1启用 0禁用',
  sort INT NOT NULL DEFAULT 0 COMMENT '排序（升序）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_shop_id (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IM FAQ知识库';
