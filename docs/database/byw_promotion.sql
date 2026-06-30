CREATE DATABASE IF NOT EXISTS byw_promotion DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE byw_promotion;

DROP TABLE IF EXISTS t_coupon;
CREATE TABLE t_coupon (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    type TINYINT NOT NULL COMMENT '1满减券 2折扣券 3无门槛券',
    discount_value DECIMAL(10,2) NOT NULL,
    min_amount DECIMAL(10,2) DEFAULT 0.00,
    total_count INT NOT NULL,
    claimed_count INT DEFAULT 0,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS t_coupon_record;
CREATE TABLE t_coupon_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    coupon_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_no VARCHAR(64),
    status TINYINT DEFAULT 0 COMMENT '0未使用 1已使用 2已过期',
    used_time DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_coupon_id (coupon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS t_seckill_activity;
CREATE TABLE t_seckill_activity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    product_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    seckill_price DECIMAL(10,2) NOT NULL,
    total_stock INT NOT NULL,
    available_stock INT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status TINYINT DEFAULT 0 COMMENT '0未开始 1进行中 2已结束',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_start_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS t_seckill_order;
CREATE TABLE t_seckill_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_no VARCHAR(64),
    status TINYINT DEFAULT 0 COMMENT '0待支付 1已支付 2已取消',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_activity_user (activity_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS t_group_buy_activity;
CREATE TABLE t_group_buy_activity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    product_id BIGINT NOT NULL,
    group_price DECIMAL(10,2) NOT NULL,
    original_price DECIMAL(10,2) NOT NULL,
    group_size INT NOT NULL DEFAULT 2,
    max_groups INT,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status TINYINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== 清空数据 ==========
TRUNCATE TABLE t_seckill_order;
TRUNCATE TABLE t_coupon_record;
TRUNCATE TABLE t_group_buy_activity;
TRUNCATE TABLE t_seckill_activity;
TRUNCATE TABLE t_coupon;

-- ========== 初始化数据 ==========

-- 优惠券
INSERT INTO t_coupon (name, type, discount_value, min_amount, total_count, claimed_count, start_time, end_time, status) VALUES
('新人专享券', 3, 50.00, 0.00, 1000, 256, '2026-06-01 00:00:00', '2026-12-31 23:59:59', 1),
('满100减20', 1, 20.00, 100.00, 5000, 1280, '2026-06-01 00:00:00', '2026-06-30 23:59:59', 1),
('满500减100', 1, 100.00, 500.00, 2000, 456, '2026-06-01 00:00:00', '2026-06-30 23:59:59', 1),
('满1000减200', 1, 200.00, 1000.00, 1000, 189, '2026-06-01 00:00:00', '2026-06-30 23:59:59', 1),
('618大促8折券', 2, 0.80, 200.00, 3000, 890, '2026-06-15 00:00:00', '2026-06-20 23:59:59', 1),
('数码品类专享', 1, 300.00, 2000.00, 500, 78, '2026-06-01 00:00:00', '2026-07-31 23:59:59', 1),
('服装品类5折券', 2, 0.50, 100.00, 1000, 345, '2026-06-01 00:00:00', '2026-06-30 23:59:59', 1),
('无门槛10元券', 3, 10.00, 0.00, 10000, 3456, '2026-01-01 00:00:00', '2026-12-31 23:59:59', 1);

-- 优惠券领取记录
INSERT INTO t_coupon_record (coupon_id, user_id, status, created_at) VALUES
(1, 2, 0, '2026-06-01 10:00:00'),
(2, 2, 1, '2026-06-02 14:00:00'),
(3, 3, 0, '2026-06-03 09:00:00'),
(4, 4, 0, '2026-06-05 16:00:00'),
(5, 2, 0, '2026-06-15 08:00:00'),
(6, 5, 0, '2026-06-10 11:00:00'),
(7, 6, 1, '2026-06-12 13:00:00'),
(8, 7, 0, '2026-06-14 10:00:00'),
(8, 8, 0, '2026-06-15 09:00:00'),
(1, 3, 0, '2026-06-02 10:00:00');

-- 秒杀活动
INSERT INTO t_seckill_activity (name, product_id, sku_id, seckill_price, total_stock, available_stock, start_time, end_time, status) VALUES
('iPhone限时秒杀', 1, 1, 7999.00, 10, 8, '2026-06-20 10:00:00', '2026-06-20 12:00:00', 0),
('华为Mate60秒杀', 2, 5, 5999.00, 20, 15, '2026-06-20 14:00:00', '2026-06-20 16:00:00', 0),
('小米14秒杀', 3, 8, 4999.00, 30, 25, '2026-06-21 10:00:00', '2026-06-21 12:00:00', 0),
('Sony耳机秒杀', 6, 14, 1699.00, 50, 42, '2026-06-21 14:00:00', '2026-06-21 16:00:00', 0),
('Nike运动鞋秒杀', 7, 16, 599.00, 100, 78, '2026-06-22 10:00:00', '2026-06-22 12:00:00', 0);

-- 团购活动
INSERT INTO t_group_buy_activity (name, product_id, group_price, original_price, group_size, max_groups, start_time, end_time, status) VALUES
('iPhone拼团优惠', 1, 8999.00, 9999.00, 3, 100, '2026-06-15 00:00:00', '2026-06-30 23:59:59', 1),
('MacBook拼团', 4, 14999.00, 16999.00, 2, 50, '2026-06-15 00:00:00', '2026-06-30 23:59:59', 1),
('iPad拼团', 9, 7499.00, 8499.00, 2, 80, '2026-06-15 00:00:00', '2026-06-30 23:59:59', 1);
