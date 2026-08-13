-- byw_user database
-- RBAC 权限体系一期：本库承载 C 端会员（t_user，纯会员、已去除 role）与平台 RBAC 五表
--   t_sys_user 平台员工 / t_sys_role 角色 / t_sys_menu 菜单权限 / t_sys_user_role 用户角色 / t_sys_role_menu 角色菜单
-- 权限标识 perm_code 写入 t_sys_menu；登录时聚合到 Redis auth:perms:{userType}:{userId}
CREATE DATABASE IF NOT EXISTS byw_user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE byw_user;

-- 用户主表（纯 C 端会员：已移除 role 字段，平台员工迁至 t_sys_user）
DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    username        VARCHAR(50) NOT NULL UNIQUE,
    password        VARCHAR(200) NOT NULL,
    phone           VARCHAR(20) UNIQUE,
    email           VARCHAR(100),
    nickname        VARCHAR(50),
    avatar          VARCHAR(500),
    gender          TINYINT DEFAULT 0 COMMENT '0未知 1男 2女',
    status          TINYINT DEFAULT 1 COMMENT '0禁用 1正常',
    user_level      TINYINT DEFAULT 0 COMMENT '0普�?1银卡 2金卡 3钻石',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT DEFAULT 0,
    INDEX idx_phone (phone),
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 收货地址
DROP TABLE IF EXISTS t_user_address;
CREATE TABLE t_user_address (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL,
    receiver_name   VARCHAR(50) NOT NULL,
    receiver_phone  VARCHAR(20) NOT NULL,
    province        VARCHAR(50),
    city            VARCHAR(50),
    district        VARCHAR(50),
    detail_address  VARCHAR(200) NOT NULL,
    is_default      TINYINT DEFAULT 0,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT DEFAULT 0,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户等级
DROP TABLE IF EXISTS t_user_level;
CREATE TABLE t_user_level (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    level_name      VARCHAR(50) NOT NULL,
    level_code      TINYINT NOT NULL UNIQUE,
    discount_rate   DECIMAL(3,2) DEFAULT 1.00,
    min_points      INT DEFAULT 0,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户商品收藏（商品级收藏）
DROP TABLE IF EXISTS t_user_favorite;
CREATE TABLE t_user_favorite (
                                 id              BIGINT PRIMARY KEY AUTO_INCREMENT,
                                 user_id         BIGINT NOT NULL,
                                 product_id      BIGINT NOT NULL,
                                 created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
                                 updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                 deleted         TINYINT DEFAULT 0,
                                 UNIQUE KEY uk_user_product (user_id, product_id),
                                 INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== RBAC 用户角色菜单权限 ==========
-- 平台员工表（平台管理端登录主体，与 C 端会员彻底分离）
DROP TABLE IF EXISTS t_sys_user;
CREATE TABLE t_sys_user (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    username        VARCHAR(50) NOT NULL UNIQUE COMMENT '登录用户名',
    password        VARCHAR(200) NOT NULL COMMENT '密码BCrypt',
    nickname        VARCHAR(50) COMMENT '姓名/昵称',
    phone           VARCHAR(20) COMMENT '联系电话',
    email           VARCHAR(100) COMMENT '邮箱',
    avatar          VARCHAR(500) COMMENT '头像',
    status          TINYINT DEFAULT 1 COMMENT '0禁用 1正常',
    last_login_at   DATETIME COMMENT '最后登录时间',
    created_by      BIGINT COMMENT '创建人（平台员工ID）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT DEFAULT 0,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台员工账号';

-- 角色表（平台与商家共用，scope 区分；shop_id 非空为店铺自定义角色，一期仅内置预设）
DROP TABLE IF EXISTS t_sys_role;
CREATE TABLE t_sys_role (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_code       VARCHAR(50) NOT NULL UNIQUE COMMENT '角色标识',
    role_name       VARCHAR(50) NOT NULL COMMENT '角色名称',
    scope           VARCHAR(20) NOT NULL COMMENT 'platform平台 / merchant商家',
    shop_id         BIGINT COMMENT 'NULL=平台角色或商家预设模板；非NULL=店铺自定义角色',
    is_preset       TINYINT DEFAULT 0 COMMENT '内置预设 0否 1是（不可删）',
    remark          VARCHAR(255) COMMENT '备注',
    status          TINYINT DEFAULT 1 COMMENT '0禁用 1正常',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT DEFAULT 0,
    INDEX idx_scope (scope),
    INDEX idx_shop_id (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

-- 菜单/权限表（平台与商家共用，scope 区分）
DROP TABLE IF EXISTS t_sys_menu;
CREATE TABLE t_sys_menu (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id       BIGINT DEFAULT 0 COMMENT '父菜单ID，0为顶级',
    menu_name       VARCHAR(50) NOT NULL COMMENT '菜单名称',
    menu_type       TINYINT NOT NULL COMMENT '1目录 2菜单 3按钮',
    scope           VARCHAR(20) NOT NULL COMMENT 'platform / merchant',
    path            VARCHAR(200) COMMENT '前端路由路径',
    perm_code       VARCHAR(100) COMMENT '权限标识，如 product:audit',
    icon            VARCHAR(50) COMMENT '图标名（ElementPlus 图标组件名）',
    sort_order      INT DEFAULT 0 COMMENT '排序',
    visible         TINYINT DEFAULT 1 COMMENT '0隐藏 1显示',
    status          TINYINT DEFAULT 1 COMMENT '0禁用 1正常',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_scope (scope),
    INDEX idx_parent (parent_id),
    INDEX idx_perm (perm_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单/权限';

-- 用户-角色关联（user_type 兼容平台员工与商家账号两类主体）
DROP TABLE IF EXISTS t_sys_user_role;
CREATE TABLE t_sys_user_role (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_type       TINYINT NOT NULL COMMENT '1平台员工 2商家账号',
    user_id         BIGINT NOT NULL,
    role_id         BIGINT NOT NULL,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role (user_type, user_id, role_id),
    INDEX idx_user (user_type, user_id),
    INDEX idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';

-- 角色-菜单关联
DROP TABLE IF EXISTS t_sys_role_menu;
CREATE TABLE t_sys_role_menu (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id         BIGINT NOT NULL,
    menu_id         BIGINT NOT NULL,
    UNIQUE KEY uk_role_menu (role_id, menu_id),
    INDEX idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联';

-- ========== 清空数据 ==========
TRUNCATE TABLE t_user_address;
TRUNCATE TABLE t_user;
TRUNCATE TABLE t_user_level;

-- ========== 初始数据 ==========
INSERT INTO t_user_level (level_name, level_code, discount_rate, min_points) VALUES
('普通用户', 0, 1.00, 0),
('银卡会员', 1, 0.95, 1000),
('金卡会员', 2, 0.90, 5000),
('钻石会员', 3, 0.85, 20000);

-- 平台员工账号 admin (admin / admin123) —— 从 t_user 迁出，独立管理
INSERT INTO t_sys_user (id, username, password, nickname, phone, status) VALUES
(1, 'admin', '$2a$10$mG4HpWhYdqOSYql91nc17OrmYxpwkchw/0Vbs5oR.txUEHBmEiVem', '超级管理员', '13800000000', 1);

-- ========== RBAC 预设角色 ==========
-- 平台角色（scope=platform）
INSERT INTO t_sys_role (id, role_code, role_name, scope, is_preset, remark) VALUES
(1, 'super_admin',      '超级管理员',   'platform', 1, '拥有全部平台菜单与权限'),
(2, 'product_auditor',  '商品审核员',   'platform', 1, '商品列表/审核/分类/品牌'),
(3, 'order_logistics',  '订单物流专员', 'platform', 1, '订单列表/物流管理'),
(4, 'review_service',   '评论客服专员', 'platform', 1, '评论管理/会员查看'),
(5, 'finance_settle',   '财务结算员',   'platform', 1, '佣金规则/提现审批'),
(6, 'operation',        '运营专员',     'platform', 1, '营销/店铺管理/入驻审核');
-- 商家预设角色模板（scope=merchant，shop_id=NULL）
INSERT INTO t_sys_role (id, role_code, role_name, scope, is_preset, remark) VALUES
(11, 'merchant_service',   '客服',     'merchant', 1, '客服工作台/评价/售后'),
(12, 'merchant_operation', '运营',     'merchant', 1, '商品管理/营销'),
(13, 'merchant_warehouse', '仓管发货', 'merchant', 1, '订单发货/售后收货'),
(14, 'merchant_finance',   '财务',     'merchant', 1, '结算与提现');

-- ========== 平台菜单树（scope=platform） ==========
INSERT INTO t_sys_menu (id, parent_id, menu_name, menu_type, scope, path, perm_code, icon, sort_order) VALUES
(100, 0,   '控制台',     2, 'platform', '/dashboard',          NULL,               'Odometer',    0),
(110, 0,   '会员管理',   1, 'platform', NULL,                  NULL,               'User',        10),
(111, 110, '会员列表',   2, 'platform', '/user/list',          'member:list',      NULL,          11),
(120, 0,   '商家管理',   1, 'platform', NULL,                  NULL,               'Shop',        20),
(121, 120, '入驻审核',   2, 'platform', '/shop/merchant',      'shop:audit',       NULL,          21),
(122, 120, '店铺管理',   2, 'platform', '/shop/list',          'shop:list',        NULL,          22),
(130, 0,   '商品管理',   1, 'platform', NULL,                  NULL,               'Goods',       30),
(131, 130, '商品列表',   2, 'platform', '/product/list',       'product:list',     NULL,          31),
(132, 130, '商品审核',   2, 'platform', '/product/audit',      'product:audit',    NULL,          32),
(133, 130, '分类管理',   2, 'platform', '/product/category',   'category:manage',  NULL,          33),
(134, 130, '品牌管理',   2, 'platform', '/product/brand',      'brand:manage',     NULL,          34),
(140, 0,   '订单管理',   1, 'platform', NULL,                  NULL,               'List',        40),
(141, 140, '订单列表',   2, 'platform', '/order/list',         'order:list',       NULL,          41),
(150, 0,   '营销管理',   1, 'platform', NULL,                  NULL,               'Present',     50),
(151, 150, '优惠券管理', 2, 'platform', '/promotion/coupon',   'coupon:manage',    NULL,          51),
(152, 150, '秒杀管理',   2, 'platform', '/promotion/seckill',  'seckill:manage',   NULL,          52),
(153, 150, '轮播图管理', 2, 'platform', '/promotion/banner',   'banner:manage',    NULL,          53),
(160, 0,   '评论管理',   1, 'platform', NULL,                  NULL,               'ChatDotRound',60),
(161, 160, '评论列表',   2, 'platform', '/review/list',        'review:manage',    NULL,          61),
(170, 0,   '物流管理',   1, 'platform', NULL,                  NULL,               'Van',         70),
(171, 170, '物流列表',   2, 'platform', '/logistics/list',     'logistics:list',   NULL,          71),
(180, 0,   '结算管理',   1, 'platform', NULL,                  NULL,               'Wallet',      80),
(181, 180, '佣金规则',   2, 'platform', '/settle/commission',  'settle:commission',NULL,          81),
(182, 180, '提现审批',   2, 'platform', '/settle/withdraw',    'settle:withdraw',  NULL,          82),
(190, 0,   '系统管理',   1, 'platform', NULL,                  NULL,               'Tools',       90),
(191, 190, '员工管理',   2, 'platform', '/system/user',        'sys:user',         NULL,          91),
(192, 190, '角色管理',   2, 'platform', '/system/role',        'sys:role',         NULL,          92),
(193, 190, '菜单管理',   2, 'platform', '/system/menu',        'sys:menu',         'Menu',         93);

-- ========== 商家菜单树（scope=merchant） ==========
INSERT INTO t_sys_menu (id, parent_id, menu_name, menu_type, scope, path, perm_code, icon, sort_order) VALUES
(200, 0,   '控制台',     2, 'merchant', '/dashboard',        NULL,                 'Odometer',    0),
(210, 0,   '商品管理',   1, 'merchant', NULL,                NULL,                 'Goods',       10),
(211, 210, '商品列表',   2, 'merchant', '/product/list',     'm:product:list',     NULL,          11),
(212, 210, '发布商品',   2, 'merchant', '/product/add',      'm:product:publish',  NULL,          12),
(220, 0,   '订单管理',   1, 'merchant', NULL,                NULL,                 'List',        20),
(221, 220, '订单列表',   2, 'merchant', '/order/list',       'm:order:list',       NULL,          21),
(222, 220, '售后管理',   2, 'merchant', '/order/after-sale', 'm:aftersale:manage', NULL,          22),
(223, 220, '订单发货',   3, 'merchant', NULL,                'm:order:ship',       NULL,          23),
(224, 220, '订单详情',   3, 'merchant', NULL,                'm:order:detail',     NULL,          24),
(230, 0,   '客服工作台', 2, 'merchant', '/im',               'm:im:workbench',     'Service',     30),
(231, 0,   '客服管理',   1, 'merchant', NULL,                NULL,                 'Headset',     35),
(232, 231, 'FAQ知识库',  2, 'merchant', '/im/faq',           'm:im:faq',           NULL,          36),
(233, 231, '客服分流',  2, 'merchant', '/im/dispatch',        'm:im:dispatch',      NULL,          37),
(234, 231, '服务评价',    2, 'merchant', '/im/satisfaction',  'm:im:satisfaction',  NULL,          38),
(240, 0,   '营销管理',   1, 'merchant', NULL,                NULL,                 'Present',     40),
(241, 240, '店铺优惠券', 2, 'merchant', '/promotion/coupon', 'm:coupon:manage',    NULL,          41),
(250, 0,   '评价管理',   1, 'merchant', NULL,                NULL,                 'ChatDotRound',50),
(251, 250, '评价列表',   2, 'merchant', '/review/list',      'm:review:manage',    NULL,          51),
(260, 0,   '店铺设置',   2, 'merchant', '/shop/info',        'm:shop:info',        'Shop',        60),
(270, 0,   '结算与提现', 2, 'merchant', '/settle/index',     'm:settle:manage',    'Wallet',      70),
(280, 0,   '员工管理',   2, 'merchant', '/staff/index',      'm:staff:manage',     'UserFilled',  80),
(290, 0,   '角色管理',   2, 'merchant', '/role/index',       'm:role:manage',      'Stamp',       90);

-- ========== 角色-菜单绑定（仅绑叶子/按钮，目录由子项自动呈现；super_admin 走 * 通配不绑） ==========
INSERT INTO t_sys_role_menu (role_id, menu_id) VALUES
-- 商品审核员
(2,131),(2,132),(2,133),(2,134),
-- 订单物流专员
(3,141),(3,171),
-- 评论客服专员
(4,161),(4,111),
-- 财务结算员
(5,181),(5,182),
-- 运营专员
(6,151),(6,152),(6,153),(6,121),(6,122),
-- 商家客服
(11,230),(11,251),(11,222),(11,224),
-- 商家运营
(12,211),(12,212),(12,241),
-- 商家仓管发货
(13,221),(13,223),(13,222),(13,224),
-- 商家财务
(14,270);

-- admin 绑定超级管理员角色（user_type=1 平台员工）
INSERT INTO t_sys_user_role (user_type, user_id, role_id) VALUES
(1, 1, 1);

-- 测试用户 (密码都是 123456)
INSERT INTO t_user (username, password, phone, nickname, avatar, gender, status, user_level) VALUES
('zhangsan', '$2a$10$mG4HpWhYdqOSYql91nc17OrmYxpwkchw/0Vbs5oR.txUEHBmEiVem', '13800001111', '张三', 'https://api.dicebear.com/7.0/persona/svg?seed=zhangsan', 1, 1, 1),
('lisi', '$2a$10$mG4HpWhYdqOSYql91nc17OrmYxpwkchw/0Vbs5oR.txUEHBmEiVem', '13800002222', '李四', 'https://api.dicebear.com/7.0/persona/svg?seed=lisi', 2, 1, 2),
('wangwu', '$2a$10$mG4HpWhYdqOSYql91nc17OrmYxpwkchw/0Vbs5oR.txUEHBmEiVem', '13800003333', '王五', 'https://api.dicebear.com/7.0/persona/svg?seed=wangwu', 1, 1, 0),
('zhaoliu', '$2a$10$mG4HpWhYdqOSYql91nc17OrmYxpwkchw/0Vbs5oR.txUEHBmEiVem', '13800004444', '赵六', 'https://api.dicebear.com/7.0/persona/svg?seed=zhaoliu', 2, 1, 3),
('sunqi', '$2a$10$mG4HpWhYdqOSYql91nc17OrmYxpwkchw/0Vbs5oR.txUEHBmEiVem', '13800005555', '孙七', 'https://api.dicebear.com/7.0/persona/svg?seed=sunqi', 1, 1, 1),
('zhouba', '$2a$10$mG4HpWhYdqOSYql91nc17OrmYxpwkchw/0Vbs5oR.txUEHBmEiVem', '13800006666', '周八', 'https://api.dicebear.com/7.0/persona/svg?seed=zhouba', 2, 1, 0),
('wujiu', '$2a$10$mG4HpWhYdqOSYql91nc17OrmYxpwkchw/0Vbs5oR.txUEHBmEiVem', '13800007777', '吴九', 'https://api.dicebear.com/7.0/persona/svg?seed=wujiu', 1, 1, 2),
('zhengshi', '$2a$10$mG4HpWhYdqOSYql91nc17OrmYxpwkchw/0Vbs5oR.txUEHBmEiVem', '13800008888', '郑十', 'https://api.dicebear.com/7.0/persona/svg?seed=zhengshi', 2, 1, 1);

-- 用户收货地址
INSERT INTO t_user_address (user_id, receiver_name, receiver_phone, province, city, district, detail_address, is_default) VALUES
(2, '张三', '13800001111', '北京市', '北京市', '朝阳区', '三里屯街道建国路88号', 1),
(2, '张三', '13800001111', '上海市', '上海市', '浦东新区', '陆家嘴街道世纪大道100号', 0),
(3, '李四', '13800002222', '广东省', '深圳市', '南山区', '科技园街道科苑路10号', 1),
(4, '王五', '13800003333', '浙江省', '杭州市', '西湖区', '文三路100号', 1),
(5, '赵六', '13800004444', '江苏省', '南京市', '鼓楼区', '中山路321号', 1),
(6, '孙七', '13800005555', '四川省', '成都市', '武侯区', '天府大道999号', 1);
