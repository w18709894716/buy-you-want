CREATE DATABASE IF NOT EXISTS byw_cart DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE byw_cart;

DROP TABLE IF EXISTS t_cart_item;
CREATE TABLE t_cart_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    sku_name VARCHAR(200),
    product_name VARCHAR(200),
    spec_data VARCHAR(500),
    product_image VARCHAR(500),
    quantity INT NOT NULL DEFAULT 1,
    price DECIMAL(10,2),
    selected TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_user_sku (user_id, sku_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
