CREATE DATABASE IF NOT EXISTS byw_order DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE byw_order;

DROP TABLE IF EXISTS t_order;
CREATE TABLE t_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    pay_amount DECIMAL(10,2),
    freight_amount DECIMAL(10,2) DEFAULT 0.00,
    discount_amount DECIMAL(10,2) DEFAULT 0.00,
    coupon_id BIGINT,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待付款 1待发货 2待收货 3已完成 4已取消 5退款中 6已退款',
    receiver_name VARCHAR(50),
    receiver_phone VARCHAR(20),
    receiver_address VARCHAR(300),
    remark VARCHAR(500),
    pay_time DATETIME,
    ship_time DATETIME,
    receive_time DATETIME,
    cancel_time DATETIME,
    cancel_reason VARCHAR(200),
    reviewed TINYINT DEFAULT 0 COMMENT '是否已评价 0未评价 1已评价',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS t_order_item;
CREATE TABLE t_order_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    product_name VARCHAR(200),
    sku_name VARCHAR(200),
    product_image VARCHAR(500),
    price DECIMAL(10,2) NOT NULL,
    quantity INT NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id),
    INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS t_order_status_log;
CREATE TABLE t_order_status_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    from_status TINYINT,
    to_status TINYINT NOT NULL,
    operator VARCHAR(50),
    remark VARCHAR(200),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== 清空数据 ==========
TRUNCATE TABLE t_order_status_log;
TRUNCATE TABLE t_order_item;
TRUNCATE TABLE t_order;

-- ========== 初始化数据 ==========

-- 订单数据
INSERT INTO t_order (order_no, user_id, total_amount, pay_amount, freight_amount, discount_amount, status, receiver_name, receiver_phone, receiver_address, pay_time, created_at) VALUES
('ORD202606010001', 2, 9999.00, 9999.00, 0.00, 0.00, 3, '张三', '13800001111', '北京市朝阳区三里屯街道建国路88号', '2026-06-01 10:30:00', '2026-06-01 10:25:00'),
('ORD202606020001', 3, 16999.00, 16999.00, 0.00, 0.00, 3, '李四', '13800002222', '广东省深圳市南山区科技园街道科苑路10号', '2026-06-02 14:20:00', '2026-06-02 14:15:00'),
('ORD202606050001', 2, 899.00, 899.00, 0.00, 0.00, 2, '张三', '13800001111', '上海市浦东新区陆家嘴街道世纪大道100号', '2026-06-05 09:00:00', '2026-06-05 08:55:00'),
('ORD202606080001', 4, 6999.00, 6999.00, 0.00, 0.00, 1, '王五', '13800003333', '浙江省杭州市西湖区文三路100号', '2026-06-08 16:45:00', '2026-06-08 16:40:00'),
('ORD202606100001', 5, 2299.00, 2299.00, 0.00, 0.00, 0, '赵六', '13800004444', '江苏省南京市鼓楼区中山路321号', NULL, '2026-06-10 20:10:00'),
('ORD202606120001', 2, 13999.00, 13999.00, 0.00, 0.00, 3, '张三', '13800001111', '北京市朝阳区三里屯街道建国路88号', '2026-06-12 11:30:00', '2026-06-12 11:25:00'),
('ORD202606130001', 6, 8499.00, 8499.00, 0.00, 0.00, 2, '孙七', '13800005555', '四川省成都市武侯区天府大道999号', '2026-06-13 13:00:00', '2026-06-13 12:55:00'),
('ORD202606140001', 3, 4299.00, 4299.00, 0.00, 0.00, 1, '李四', '13800002222', '广东省深圳市南山区科技园街道科苑路10号', '2026-06-14 15:20:00', '2026-06-14 15:15:00'),
('ORD202606150001', 7, 6499.00, 6499.00, 0.00, 0.00, 0, '吴九', '13800007777', '北京市朝阳区建国门外大街1号', NULL, '2026-06-15 08:30:00'),
('ORD202606160001', 8, 1099.00, 1099.00, 0.00, 0.00, 4, '郑十', '13800008888', '上海市黄浦区南京东路100号', NULL, '2026-06-16 09:00:00');

-- 订单商品
INSERT INTO t_order_item (order_id, order_no, user_id, product_id, sku_id, product_name, sku_name, product_image, price, quantity, subtotal) VALUES
(1, 'ORD202606010001', 2, 1, 1, 'Apple iPhone 15 Pro Max', 'iPhone 15 Pro Max 256GB 原色钛金属', 'https://store.storeimages.cdn-apple.com/4982/as-images.apple.com/is/iphone-15-pro-max-hero-select-202309-6-7inch?wid=940&hei=781&fmt=webql', 9999.00, 1, 9999.00),
(2, 'ORD202606020001', 3, 4, 11, 'MacBook Pro 14英寸', 'MacBook Pro 14 M3 Pro 512GB', 'https://store.storeimages.cdn-apple.com/4982/as-images.apple.com/is/mbp-14-touchbar-spacegray-select-202310?wid=904', 16999.00, 1, 16999.00),
(3, 'ORD202606050001', 2, 7, 16, 'Nike Air Max 270', 'Nike Air Max 270 42码', 'https://static.nike.com/a/images/t_PDP_1728_v1/f_auto,q_auto:eco/af42461a-56ba-4b84-b3e8-55e0c2bfdc5b/air-max-270-shoe-9Q2F7W.png', 899.00, 1, 899.00),
(4, 'ORD202606080001', 4, 2, 5, '华为 Mate 60 Pro', '华为 Mate 60 Pro 256GB 雅丹黑', 'https://res.vmallres.com/pimages/uomcdn/CN/pms/202308/gbom/6941487299815/800_800_945A11F73FF956C94E4869F365329376mp.png', 6999.00, 1, 6999.00),
(5, 'ORD202606100001', 5, 6, 14, 'Sony WH-1000XM5', 'Sony WH-1000XM5 黑色', 'https://m.media-amazon.com/images/I/51aX2aQWURL._AC_SL1500_.jpg', 2299.00, 1, 2299.00),
(6, 'ORD202606120001', 2, 1, 4, 'Apple iPhone 15 Pro Max', 'iPhone 15 Pro Max 1TB 原色钛金属', 'https://store.storeimages.cdn-apple.com/4982/as-images.apple.com/is/iphone-15-pro-max-hero-select-202309-6-7inch?wid=940&hei=781&fmt=webql', 13999.00, 1, 13999.00),
(7, 'ORD202606130001', 6, 9, 21, 'Apple iPad Pro 12.9英寸', 'iPad Pro 12.9英寸 128GB', 'https://store.storeimages.cdn-apple.com/4982/as-images.apple.com/is/ipad-pro-13-select-202210?wid=940', 8499.00, 1, 8499.00),
(8, 'ORD202606140001', 3, 10, 23, '戴尔 U2723QE 4K显示器', '戴尔 U2723QE 4K显示器', 'https://i.dell.com/is/image/DellContent/content/dam/ss2/product-images/dell-client-products/electronics-and-accessories/dell-displays/home-office/u2723qe/cp/images/display-u2723qe-cp.psd', 4299.00, 1, 4299.00),
(9, 'ORD202606150001', 7, 3, 8, '小米14 Ultra', '小米14 Ultra 256GB 黑色', 'https://i8.mifile.cn/a1/pms/1677049830.41893948.jpg', 6499.00, 1, 6499.00),
(10, 'ORD202606160001', 8, 8, 19, 'Adidas Ultraboost 22', 'Adidas Ultraboost 22 41码', 'https://assets.adidas.com/images/h_840,f_auto,q_auto,fl_lossy,c_fill,g_auto/1e5104d60a964b8fa7c3ac9d00e34205_9366/Ultraboost_22_Shoes_White_GX3605_01_standard.jpg', 1099.00, 1, 1099.00);

-- 订单状态日志
INSERT INTO t_order_status_log (order_id, from_status, to_status, operator, remark, created_at) VALUES
(1, NULL, 0, 'system', '订单创建', '2026-06-01 10:25:00'),
(1, 0, 1, 'system', '支付成功', '2026-06-01 10:30:00'),
(1, 1, 2, 'admin', '已发货', '2026-06-02 09:00:00'),
(1, 2, 3, 'system', '确认收货', '2026-06-04 18:00:00'),
(2, NULL, 0, 'system', '订单创建', '2026-06-02 14:15:00'),
(2, 0, 1, 'system', '支付成功', '2026-06-02 14:20:00'),
(2, 1, 2, 'admin', '已发货', '2026-06-03 10:00:00'),
(2, 2, 3, 'system', '确认收货', '2026-06-05 15:00:00'),
(3, NULL, 0, 'system', '订单创建', '2026-06-05 08:55:00'),
(3, 0, 1, 'system', '支付成功', '2026-06-05 09:00:00'),
(3, 1, 2, 'admin', '已发货', '2026-06-06 14:00:00'),
(4, NULL, 0, 'system', '订单创建', '2026-06-08 16:40:00'),
(4, 0, 1, 'system', '支付成功', '2026-06-08 16:45:00'),
(5, NULL, 0, 'system', '订单创建', '2026-06-10 20:10:00'),
(6, NULL, 0, 'system', '订单创建', '2026-06-12 11:25:00'),
(6, 0, 1, 'system', '支付成功', '2026-06-12 11:30:00'),
(6, 1, 2, 'admin', '已发货', '2026-06-13 09:00:00'),
(6, 2, 3, 'system', '确认收货', '2026-06-14 20:00:00'),
(7, NULL, 0, 'system', '订单创建', '2026-06-13 12:55:00'),
(7, 0, 1, 'system', '支付成功', '2026-06-13 13:00:00'),
(7, 1, 2, 'admin', '已发货', '2026-06-14 10:00:00'),
(8, NULL, 0, 'system', '订单创建', '2026-06-14 15:15:00'),
(8, 0, 1, 'system', '支付成功', '2026-06-14 15:20:00'),
(9, NULL, 0, 'system', '订单创建', '2026-06-15 08:30:00'),
(10, NULL, 0, 'system', '订单创建', '2026-06-16 09:00:00'),
(10, 0, 4, 'user', '用户取消订单', '2026-06-16 09:30:00');
