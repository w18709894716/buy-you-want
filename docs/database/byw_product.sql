CREATE DATABASE IF NOT EXISTS byw_product DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE byw_product;

CREATE TABLE t_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    parent_id BIGINT DEFAULT 0,
    level TINYINT NOT NULL,
    sort_order INT DEFAULT 0,
    icon VARCHAR(500),
    is_show TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_parent_id (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE t_brand (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    logo VARCHAR(500),
    sort_order INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE t_product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    subtitle VARCHAR(500),
    category_id BIGINT NOT NULL,
    brand_id BIGINT,
    main_image VARCHAR(500),
    sub_images TEXT,
    detail_html MEDIUMTEXT,
    status TINYINT DEFAULT 0 COMMENT '0草稿 1上架 2下架',
    sales_count INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_category_id (category_id),
    INDEX idx_brand_id (brand_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE t_sku (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    sku_code VARCHAR(100) UNIQUE,
    sku_name VARCHAR(200),
    spec_data JSON,
    price DECIMAL(10,2) NOT NULL,
    cost_price DECIMAL(10,2),
    stock INT NOT NULL DEFAULT 0,
    lock_stock INT NOT NULL DEFAULT 0,
    image VARCHAR(500),
    weight DECIMAL(8,2),
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
