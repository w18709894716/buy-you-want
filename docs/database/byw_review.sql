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

-- ========== 清空数据 ==========
TRUNCATE TABLE t_review_image;
TRUNCATE TABLE t_review;

-- ========== 初始化数据 ==========

-- 商品评价
INSERT INTO t_review (order_no, user_id, product_id, sku_id, rating, content, has_image, is_anonymous, status, created_at) VALUES
('ORD202606010001', 2, 1, 1, 5, 'iPhone 15 Pro Max 真的很棒！A17 Pro芯片性能强劲，钛金属设计手感很好，相机拍照效果惊艳！', 1, 0, 1, '2026-06-05 10:00:00'),
('ORD202606010001', 2, 1, 1, 5, '物流很快，包装完好，手机颜值很高，推荐购买！', 0, 0, 1, '2026-06-06 15:30:00'),
('ORD202606020001', 3, 4, 11, 5, 'MacBook Pro M3 Pro芯片性能太强了，剪辑视频非常流畅，屏幕显示效果极佳！', 1, 0, 1, '2026-06-07 09:00:00'),
('ORD202606050001', 2, 7, 16, 4, '鞋子穿着很舒服，气垫缓震效果不错，就是鞋带有点短。', 0, 0, 1, '2026-06-10 14:00:00'),
('ORD202606120001', 2, 1, 4, 5, '1TB版本存储空间充足，拍照效果非常棒，夜景模式很惊艳！', 1, 0, 1, '2026-06-15 11:00:00'),
('ORD202606130001', 6, 9, 21, 4, 'iPad Pro屏幕很大很清晰，用来看视频和办公都很合适，就是价格有点贵。', 0, 1, 1, '2026-06-16 16:00:00'),
('ORD202606020001', 3, 4, 11, 5, '键盘手感很好，屏幕色彩准确，做设计工作非常合适！', 1, 0, 1, '2026-06-08 10:00:00'),
('ORD202606140001', 3, 10, 23, 5, '4K显示器色彩非常准确，IPS Black面板对比度高，办公看文档很舒服。', 0, 0, 1, '2026-06-16 14:00:00');

-- 评价图片
INSERT INTO t_review_image (review_id, image_url, type, created_at) VALUES
(1, 'https://images.unsplash.com/photo-1592750475338-74b7b21085ab?w=800', 0, '2026-06-05 10:00:00'),
(1, 'https://images.unsplash.com/photo-1696446702183-cbd13d78e1e7?w=800', 0, '2026-06-05 10:00:00'),
(3, 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=800', 0, '2026-06-07 09:00:00'),
(5, 'https://images.unsplash.com/photo-1510557880182-3d4d3cba35a5?w=800', 0, '2026-06-15 11:00:00'),
(7, 'https://images.unsplash.com/photo-1541807084-5c52b6b3adef?w=800', 0, '2026-06-08 10:00:00');
