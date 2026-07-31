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
    UNIQUE KEY uk_user_shop (user_id, shop_id, deleted),
    INDEX idx_user_id (user_id),
    INDEX idx_shop_id (shop_id),
    INDEX idx_last_message_time (last_message_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服会话表';

-- ========== 说明 ==========
-- 消息流存 MongoDB（库 byw_im，集合 im_messages），无需在此建表。
-- 文档结构见 com.byw.im.document.ImMessage：
--   { conversationId, senderId, senderRole, shopId, userId, type, content, extra, read, createdAt }
