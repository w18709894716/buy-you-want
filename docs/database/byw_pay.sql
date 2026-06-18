CREATE DATABASE IF NOT EXISTS byw_pay DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE byw_pay;

CREATE TABLE t_pay_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    pay_no VARCHAR(64) NOT NULL UNIQUE,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    pay_channel VARCHAR(20) NOT NULL,
    status TINYINT DEFAULT 0 COMMENT '0待支付 1支付成功 2支付失败 3已退款',
    channel_trade_no VARCHAR(100),
    pay_time DATETIME,
    callback_content TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_order_no (order_no),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE t_refund_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    refund_no VARCHAR(64) NOT NULL UNIQUE,
    pay_no VARCHAR(64) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    refund_amount DECIMAL(10,2) NOT NULL,
    reason VARCHAR(200),
    status TINYINT DEFAULT 0 COMMENT '0处理中 1成功 2失败',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
