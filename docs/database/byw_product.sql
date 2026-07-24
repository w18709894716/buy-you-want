CREATE DATABASE IF NOT EXISTS byw_product DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE byw_product;

DROP TABLE IF EXISTS t_category;
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

DROP TABLE IF EXISTS t_brand;
CREATE TABLE t_brand (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    first_letter VARCHAR(10),
    logo VARCHAR(500),
    sort_order INT DEFAULT 0,
    status TINYINT DEFAULT 1 COMMENT '0禁用 1启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS t_product;
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

DROP TABLE IF EXISTS t_sku;
CREATE TABLE t_sku (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    sku_code VARCHAR(100),
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
    INDEX idx_product_id (product_id),
    UNIQUE KEY uk_product_sku_code (product_id, sku_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== 清空数据 ==========
TRUNCATE TABLE t_sku;
TRUNCATE TABLE t_product;
TRUNCATE TABLE t_brand;
TRUNCATE TABLE t_category;

-- ========== 初始化数据 ==========

-- 商品分类
INSERT INTO t_category (name, parent_id, level, sort_order, icon, is_show) VALUES
-- 一级分类
('手机数码', 0, 1, 1, 'https://cdn-icons-png.flaticon.com/512/5682/5682154.png', 1),
('电脑办公', 0, 1, 2, 'https://cdn-icons-png.flaticon.com/512/2290/2290572.png', 1),
('服装鞋帽', 0, 1, 3, 'https://cdn-icons-png.flaticon.com/512/3531/3531849.png', 1),
('食品饮料', 0, 1, 4, 'https://cdn-icons-png.flaticon.com/512/3595/3595455.png', 1),
('家居家装', 0, 1, 5, 'https://cdn-icons-png.flaticon.com/512/2429/2429664.png', 1),
('美妆护肤', 0, 1, 6, 'https://cdn-icons-png.flaticon.com/512/1254/1254740.png', 1),
('运动户外', 0, 1, 7, 'https://cdn-icons-png.flaticon.com/512/773/773849.png', 1),
('母婴玩具', 0, 1, 8, 'https://cdn-icons-png.flaticon.com/512/3069/3069186.png', 1),
-- 二级分类 - 手机数码
('手机', 1, 2, 1, NULL, 1),
('平板电脑', 1, 2, 2, NULL, 1),
('手机配件', 1, 2, 3, NULL, 1),
('智能设备', 1, 2, 4, NULL, 1),
-- 二级分类 - 电脑办公
('笔记本', 2, 2, 1, NULL, 1),
('台式机', 2, 2, 2, NULL, 1),
('显示器', 2, 2, 3, NULL, 1),
('键盘鼠标', 2, 2, 4, NULL, 1),
-- 二级分类 - 服装鞋帽
('男装', 3, 2, 1, NULL, 1),
('女装', 3, 2, 2, NULL, 1),
('男鞋', 3, 2, 3, NULL, 1),
('女鞋', 3, 2, 4, NULL, 1),
-- 二级分类 - 食品饮料
('零食', 4, 2, 1, NULL, 1),
('饮料', 4, 2, 2, NULL, 1),
-- 二级分类 - 家居家装
('家具', 5, 2, 1, NULL, 1),
('装饰', 5, 2, 2, NULL, 1),
-- 二级分类 - 美妆护肤
('面部护肤', 6, 2, 1, NULL, 1),
('彩妆', 6, 2, 2, NULL, 1),
('香水', 6, 2, 3, NULL, 1),
('美容工具', 6, 2, 4, NULL, 1),
-- 二级分类 - 运动户外
('运动鞋', 7, 2, 1, NULL, 1),
('运动服饰', 7, 2, 2, NULL, 1),
('健身器材', 7, 2, 3, NULL, 1),
('户外装备', 7, 2, 4, NULL, 1),
-- 二级分类 - 母婴玩具
('奶粉辅食', 8, 2, 1, NULL, 1),
('纸尿裤', 8, 2, 2, NULL, 1),
('童装童鞋', 8, 2, 3, NULL, 1),
('玩具', 8, 2, 4, NULL, 1);

-- 品牌
INSERT INTO t_brand (name, first_letter, logo, sort_order, status) VALUES
('Apple', 'A', 'https://dummyimage.com/200x100/000/fff&text=Apple', 1, 1),
('华为', 'H', 'https://dummyimage.com/200x100/c70000/fff&text=HUAWEI', 2, 1),
('小米', 'X', 'https://dummyimage.com/200x100/ff6900/fff&text=Xiaomi', 3, 1),
('Nike', 'N', 'https://dummyimage.com/200x100/111/fff&text=Nike', 4, 1),
('Adidas', 'A', 'https://dummyimage.com/200x100/000/fff&text=Adidas', 5, 1),
('Sony', 'S', 'https://dummyimage.com/200x100/000/fff&text=Sony', 6, 1),
('联想', 'L', 'https://dummyimage.com/200x100/e60012/fff&text=Lenovo', 7, 1),
('戴尔', 'D', 'https://dummyimage.com/200x100/007db8/fff&text=Dell', 8, 1);

-- 商品
INSERT INTO t_product (name, subtitle, category_id, brand_id, main_image, sub_images, detail_html, status, sales_count) VALUES
('Apple iPhone 15 Pro Max', 'A17 Pro芯片，钛金属设计', 9, 1, 'https://dummyimage.com/400x400/333/fff&text=iPhone+15+Pro+Max', '[]', '<h3>强大超乎想象</h3><p>iPhone 15 Pro Max，搭载A17 Pro芯片，钛金属设计，4800万像素专业级相机系统。</p>', 1, 3280),
('华为 Mate 60 Pro', '麒麟9000S，卫星通话', 9, 2, 'https://dummyimage.com/400x400/c70000/fff&text=Mate+60+Pro', '[]', '<h3>遥遥领先</h3><p>华为Mate 60 Pro，搭载麒麟9000S芯片，支持卫星通话。</p>', 1, 2156),
('小米14 Ultra', '徕卡影像旗舰', 9, 3, 'https://dummyimage.com/400x400/ff6900/fff&text=Mi+14+Ultra', '[]', '<h3>影像新境界</h3><p>小米14 Ultra，搭载第三代骁龙8，徕卡光学全焦段四摄。</p>', 1, 1890),
('MacBook Pro 14英寸', 'M3 Pro芯片，Liquid Retina XDR显示屏', 13, 1, 'https://dummyimage.com/400x400/555/fff&text=MacBook+Pro+14', '[]', '<h3>专业性能</h3><p>搭载M3 Pro芯片，最长可达18小时电池续航。</p>', 1, 956),
('联想 拯救者Y9000P', 'i9-13900HX，RTX4060', 13, 7, 'https://dummyimage.com/400x400/e60012/fff&text=Y9000P', '[]', '<h3>游戏性能怪兽</h3><p>搭载第13代酷睿i9处理器，RTX4060显卡。</p>', 1, 678),
('Sony WH-1000XM5', '行业领先降噪', 11, 6, 'https://dummyimage.com/400x400/222/fff&text=Sony+XM5', '[]', '<h3>沉浸音乐</h3><p>索尼旗舰降噪耳机，30小时续航。</p>', 1, 1456),
('Nike Air Max 270', '气垫缓震，舒适透气', 19, 4, 'https://dummyimage.com/400x400/111/fff&text=Air+Max+270', '[]', '<h3>经典气垫</h3><p>Nike Air Max 270，大气垫缓震，舒适透气。</p>', 1, 892),
('Adidas Ultraboost 22', 'Boost科技，能量回馈', 19, 5, 'https://dummyimage.com/400x400/000/fff&text=Ultraboost+22', '[]', '<h3>跑步新体验</h3><p>Adidas Ultraboost 22，Boost中底科技。</p>', 1, 567),
('Apple iPad Pro 12.9英寸', 'M2芯片，Liquid Retina XDR', 10, 1, 'https://dummyimage.com/400x400/444/fff&text=iPad+Pro+12.9', '[]', '<h3>生产力工具</h3><p>搭载M2芯片，支持Apple Pencil悬停功能。</p>', 1, 1234),
('戴尔 U2723QE 4K显示器', '27英寸，IPS Black面板', 15, 8, 'https://dummyimage.com/400x400/007db8/fff&text=Dell+U2723QE', '[]', '<h3>专业色彩</h3><p>4K IPS Black面板，98% DCI-P3色域。</p>', 1, 445);

-- SKU (商品规格)
INSERT INTO t_sku (product_id, sku_code, sku_name, spec_data, price, cost_price, stock, lock_stock, image, weight) VALUES
-- iPhone 15 Pro Max
(1, 'IP15PM-256-NT', 'iPhone 15 Pro Max 256GB 原色钛金属', '{"颜色":"原色钛金属","存储":"256GB"}', 9999.00, 7500.00, 120, 0, NULL, 0.22),
(1, 'IP15PM-256-BT', 'iPhone 15 Pro Max 256GB 蓝色钛金属', '{"颜色":"蓝色钛金属","存储":"256GB"}', 9999.00, 7500.00, 85, 0, NULL, 0.22),
(1, 'IP15PM-512-NT', 'iPhone 15 Pro Max 512GB 原色钛金属', '{"颜色":"原色钛金属","存储":"512GB"}', 11999.00, 9000.00, 60, 0, NULL, 0.22),
(1, 'IP15PM-1T-NT', 'iPhone 15 Pro Max 1TB 原色钛金属', '{"颜色":"原色钛金属","存储":"1TB"}', 13999.00, 10500.00, 30, 0, NULL, 0.22),
-- 华为 Mate 60 Pro
(2, 'HW-M60P-256-BK', '华为 Mate 60 Pro 256GB 雅丹黑', '{"颜色":"雅丹黑","存储":"256GB"}', 6999.00, 5200.00, 200, 0, NULL, 0.21),
(2, 'HW-M60P-512-BK', '华为 Mate 60 Pro 512GB 雅丹黑', '{"颜色":"雅丹黑","存储":"512GB"}', 7999.00, 6000.00, 150, 0, NULL, 0.21),
(2, 'HW-M60P-256-WH', '华为 Mate 60 Pro 256GB 白沙银', '{"颜色":"白沙银","存储":"256GB"}', 6999.00, 5200.00, 180, 0, NULL, 0.21),
-- 小米14 Ultra
(3, 'XM-14U-256-BK', '小米14 Ultra 256GB 黑色', '{"颜色":"黑色","存储":"256GB"}', 6499.00, 4800.00, 45, 0, NULL, 0.22),
(3, 'XM-14U-512-BK', '小米14 Ultra 512GB 黑色', '{"颜色":"黑色","存储":"512GB"}', 6999.00, 5200.00, 35, 0, NULL, 0.22),
(3, 'XM-14U-1T-WH', '小米14 Ultra 1TB 白色', '{"颜色":"白色","存储":"1TB"}', 7799.00, 5800.00, 20, 0, NULL, 0.22),
-- MacBook Pro
(4, 'MBP-14-M3P-512', 'MacBook Pro 14 M3 Pro 512GB', '{"颜色":"深空黑色","内存":"18GB","存储":"512GB"}', 16999.00, 13000.00, 25, 0, NULL, 1.61),
(4, 'MBP-14-M3P-1T', 'MacBook Pro 14 M3 Pro 1TB', '{"颜色":"深空黑色","内存":"18GB","存储":"1TB"}', 19999.00, 15000.00, 15, 0, NULL, 1.61),
-- 联想拯救者
(5, 'LZ-Y9P-i9-1T', '拯救者Y9000P i9/32G/1T', '{"颜色":"灰色","内存":"32GB","存储":"1TB"}', 12999.00, 10000.00, 30, 0, NULL, 2.50),
-- Sony耳机
(6, 'SN-XM5-BK', 'Sony WH-1000XM5 黑色', '{"颜色":"黑色"}', 2299.00, 1500.00, 85, 0, NULL, 0.25),
(6, 'SN-XM5-SV', 'Sony WH-1000XM5 铂金银', '{"颜色":"铂金银"}', 2299.00, 1500.00, 60, 0, NULL, 0.25),
-- Nike运动鞋
(7, 'NK-AM270-40', 'Nike Air Max 270 40码', '{"颜色":"黑白","尺码":"40"}', 899.00, 500.00, 50, 0, NULL, 0.35),
(7, 'NK-AM270-42', 'Nike Air Max 270 42码', '{"颜色":"黑白","尺码":"42"}', 899.00, 500.00, 80, 0, NULL, 0.35),
(7, 'NK-AM270-44', 'Nike Air Max 270 44码', '{"颜色":"黑白","尺码":"44"}', 899.00, 500.00, 40, 0, NULL, 0.35),
-- Adidas运动鞋
(8, 'AD-UB22-41', 'Adidas Ultraboost 22 41码', '{"颜色":"白色","尺码":"41"}', 1099.00, 600.00, 60, 0, NULL, 0.32),
(8, 'AD-UB22-43', 'Adidas Ultraboost 22 43码', '{"颜色":"白色","尺码":"43"}', 1099.00, 600.00, 75, 0, NULL, 0.32),
-- iPad Pro
(9, 'IPAD-PRO-129-128', 'iPad Pro 12.9英寸 128GB', '{"颜色":"深空灰色","存储":"128GB"}', 8499.00, 6500.00, 40, 0, NULL, 0.68),
(9, 'IPAD-PRO-129-256', 'iPad Pro 12.9英寸 256GB', '{"颜色":"深空灰色","存储":"256GB"}', 9499.00, 7200.00, 30, 0, NULL, 0.68),
-- 戴尔显示器
(10, 'DL-U2723QE', '戴尔 U2723QE 4K显示器', '{"尺寸":"27英寸"}', 4299.00, 3200.00, 20, 0, NULL, 6.30);

-- ----------------------------
-- 首页轮播 Banner 表
-- link_type: 1=搜索关键词 2=商品详情 3=商品分类 4=自定义链接
-- 定时上下线通过查询时按 start_time/end_time 过滤实现，无需定时任务
-- ----------------------------
DROP TABLE IF EXISTS t_banner;
CREATE TABLE t_banner (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL COMMENT 'Banner标题(后台识别/无图时展示)',
    image_url VARCHAR(500) COMMENT '轮播图片URL(MinIO)',
    link_type TINYINT NOT NULL DEFAULT 1 COMMENT '跳转类型:1搜索关键词 2商品详情 3商品分类 4自定义链接',
    link_value VARCHAR(500) COMMENT '跳转值:关键词/商品ID/分类名/URL',
    position TINYINT NOT NULL DEFAULT 0 COMMENT '展示位置:0轮播 1右侧活动位',
    sort_order INT DEFAULT 0 COMMENT '排序,越小越靠前',
    status TINYINT DEFAULT 1 COMMENT '0禁用 1启用',
    start_time DATETIME COMMENT '上线时间(空表示立即上线)',
    end_time DATETIME COMMENT '下线时间(空表示永久有效)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_pos_status_sort (position, status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页轮播Banner/活动位';

-- 轮播图种子数据(position=0,image_url 留空,前端以渐变+标题兜底,可在管理端上传真实图片)
INSERT INTO t_banner (title, image_url, link_type, link_value, position, sort_order, status) VALUES
('买你所想，尽在此刻', NULL, 1, '手机', 0, 1, 1),
('数码新品上市特惠', NULL, 3, '手机数码', 0, 2, 1),
('全场满减优惠', NULL, 1, '耳机', 0, 3, 1);

-- 右侧活动位种子数据(position=1,建议配 4 个,首页右侧2x2活动图框)
INSERT INTO t_banner (title, image_url, link_type, link_value, position, sort_order, status) VALUES
('新人专享', NULL, 4, '/search?sort=hot', 1, 1, 1),
('限时秒杀', NULL, 1, '笔记本', 1, 2, 1),
('品牌甄选', NULL, 3, '手机数码', 1, 3, 1),
('超值好物', NULL, 1, '耳机', 1, 4, 1);
