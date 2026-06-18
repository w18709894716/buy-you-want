CREATE DATABASE IF NOT EXISTS byw_review DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE byw_review;

CREATE TABLE t_review (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    rating TINYINT NOT NULL COMMENT '1-5星',
    content VARCHAR(1000),
    has_image TINYINT DEFAULT 0,
    is_anonymous TINYINT DEFAULT 0,
    status TINYINT DEFAULT 1 COMMENT '0隐藏 1显示',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_product_id (product_id),
    INDEX idx_order_no (order_no),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE t_review_image (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    review_id BIGINT NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    type TINYINT DEFAULT 0 COMMENT '0初评 1追评',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_review_id (review_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
