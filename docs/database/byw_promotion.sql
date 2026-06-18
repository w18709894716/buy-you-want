CREATE DATABASE IF NOT EXISTS byw_promotion DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE byw_promotion;

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

CREATE TABLE t_seckill_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_no VARCHAR(64),
    status TINYINT DEFAULT 0 COMMENT '0待支付 1已支付 2已取消',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_activity_user (activity_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
