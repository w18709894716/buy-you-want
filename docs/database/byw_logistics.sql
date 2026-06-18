CREATE DATABASE IF NOT EXISTS byw_logistics DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE byw_logistics;

CREATE TABLE t_logistics_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    company_code VARCHAR(50),
    company_name VARCHAR(100),
    tracking_no VARCHAR(100),
    sender_name VARCHAR(50),
    sender_phone VARCHAR(20),
    sender_address VARCHAR(300),
    receiver_name VARCHAR(50),
    receiver_phone VARCHAR(20),
    receiver_address VARCHAR(300),
    status TINYINT DEFAULT 0 COMMENT '0已揽收 1运输中 2派送中 3已签收',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_order_no (order_no),
    INDEX idx_tracking_no (tracking_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE t_logistics_trace (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    logistics_id BIGINT NOT NULL,
    tracking_no VARCHAR(100),
    description VARCHAR(500),
    location VARCHAR(200),
    trace_time DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_logistics_id (logistics_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
