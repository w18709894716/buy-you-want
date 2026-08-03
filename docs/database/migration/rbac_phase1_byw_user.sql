-- ============================================================
-- RBAC 权限体系一期 · 增量迁移脚本（byw_user 库）
-- 用途：在【保留存量会员/地址/等级/收藏数据】前提下升级到 RBAC 结构
-- 特性：可重复执行（幂等）——列变更用 information_schema 守卫、种子用 INSERT IGNORE
-- ⚠ 执行前务必备份：  mysqldump -u root -p byw_user > byw_user_backup.sql
-- ============================================================
USE byw_user;

-- ------------------------------------------------------------
-- 1. t_user：迁出平台管理员 + 去除 role 列（仅当 role 列仍存在时执行）
-- ------------------------------------------------------------
SET @has_role := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'byw_user' AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'role');

-- 1.1 删除旧的平台管理员会员记录（管理员已迁至 t_sys_user，纯会员保留）
SET @sql := IF(@has_role > 0,
  'DELETE FROM t_user WHERE role = ''platform_admin''',
  'SELECT ''[skip] t_user.role 已移除，无需清理管理员'' AS msg');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 1.2 删除 role 列
SET @sql := IF(@has_role > 0,
  'ALTER TABLE t_user DROP COLUMN role',
  'SELECT ''[skip] t_user.role 已移除'' AS msg');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ------------------------------------------------------------
-- 2. 新建 RBAC 五表（已存在则跳过，不动任何已有数据）
-- ------------------------------------------------------------
-- 平台员工表
CREATE TABLE IF NOT EXISTS t_sys_user (
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

-- 角色表
CREATE TABLE IF NOT EXISTS t_sys_role (
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

-- 菜单/权限表
CREATE TABLE IF NOT EXISTS t_sys_menu (
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

-- 用户-角色关联
CREATE TABLE IF NOT EXISTS t_sys_user_role (
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
CREATE TABLE IF NOT EXISTS t_sys_role_menu (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id         BIGINT NOT NULL,
    menu_id         BIGINT NOT NULL,
    UNIQUE KEY uk_role_menu (role_id, menu_id),
    INDEX idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联';

-- ------------------------------------------------------------
-- 3. RBAC 种子数据（INSERT IGNORE 幂等，重复执行不会重复插入）
-- ------------------------------------------------------------
-- 3.1 平台员工 admin (admin / admin123)
INSERT IGNORE INTO t_sys_user (id, username, password, nickname, phone, status) VALUES
(1, 'admin', '$2a$10$mG4HpWhYdqOSYql91nc17OrmYxpwkchw/0Vbs5oR.txUEHBmEiVem', '超级管理员', '13800000000', 1);

-- 3.2 平台预设角色
INSERT IGNORE INTO t_sys_role (id, role_code, role_name, scope, is_preset, remark) VALUES
(1, 'super_admin',      '超级管理员',   'platform', 1, '拥有全部平台菜单与权限'),
(2, 'product_auditor',  '商品审核员',   'platform', 1, '商品列表/审核/分类/品牌'),
(3, 'order_logistics',  '订单物流专员', 'platform', 1, '订单列表/物流管理'),
(4, 'review_service',   '评论客服专员', 'platform', 1, '评论管理/会员查看'),
(5, 'finance_settle',   '财务结算员',   'platform', 1, '佣金规则/提现审批'),
(6, 'operation',        '运营专员',     'platform', 1, '营销/店铺管理/入驻审核');
-- 3.3 商家预设角色模板
INSERT IGNORE INTO t_sys_role (id, role_code, role_name, scope, is_preset, remark) VALUES
(11, 'merchant_service',   '客服',     'merchant', 1, '客服工作台/评价/售后'),
(12, 'merchant_operation', '运营',     'merchant', 1, '商品管理/营销'),
(13, 'merchant_warehouse', '仓管发货', 'merchant', 1, '订单发货/售后收货'),
(14, 'merchant_finance',   '财务',     'merchant', 1, '结算与提现');

-- 3.4 平台菜单树（scope=platform）
INSERT IGNORE INTO t_sys_menu (id, parent_id, menu_name, menu_type, scope, path, perm_code, icon, sort_order) VALUES
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
(192, 190, '角色管理',   2, 'platform', '/system/role',        'sys:role',         NULL,          92);

-- 3.5 商家菜单树（scope=merchant）
INSERT IGNORE INTO t_sys_menu (id, parent_id, menu_name, menu_type, scope, path, perm_code, icon, sort_order) VALUES
(200, 0,   '控制台',     2, 'merchant', '/dashboard',        NULL,                 'Odometer',    0),
(210, 0,   '商品管理',   1, 'merchant', NULL,                NULL,                 'Goods',       10),
(211, 210, '商品列表',   2, 'merchant', '/product/list',     'm:product:list',     NULL,          11),
(212, 210, '发布商品',   2, 'merchant', '/product/add',      'm:product:publish',  NULL,          12),
(220, 0,   '订单管理',   1, 'merchant', NULL,                NULL,                 'List',        20),
(221, 220, '订单列表',   2, 'merchant', '/order/list',       'm:order:list',       NULL,          21),
(222, 220, '售后管理',   2, 'merchant', '/order/after-sale', 'm:aftersale:manage', NULL,          22),
(223, 220, '订单发货',   3, 'merchant', NULL,                'm:order:ship',       NULL,          23),
(230, 0,   '客服工作台', 2, 'merchant', '/im',               'm:im:workbench',     'Service',     30),
(240, 0,   '营销管理',   1, 'merchant', NULL,                NULL,                 'Present',     40),
(241, 240, '店铺优惠券', 2, 'merchant', '/promotion/coupon', 'm:coupon:manage',    NULL,          41),
(250, 0,   '评价管理',   1, 'merchant', NULL,                NULL,                 'ChatDotRound',50),
(251, 250, '评价列表',   2, 'merchant', '/review/list',      'm:review:manage',    NULL,          51),
(260, 0,   '店铺设置',   2, 'merchant', '/shop/info',        'm:shop:info',        'Shop',        60),
(270, 0,   '结算与提现', 2, 'merchant', '/settle/index',     'm:settle:manage',    'Wallet',      70),
(280, 0,   '员工管理',   2, 'merchant', '/staff/index',      'm:staff:manage',     'UserFilled',  80),
(290, 0,   '角色管理',   2, 'merchant', '/role/index',       'm:role:manage',      'Stamp',       90);

-- 3.6 角色-菜单绑定（super_admin 走 * 通配不绑）
INSERT IGNORE INTO t_sys_role_menu (role_id, menu_id) VALUES
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
(11,230),(11,251),(11,222),
-- 商家运营
(12,211),(12,212),(12,241),
-- 商家仓管发货
(13,221),(13,223),(13,222),
-- 商家财务
(14,270);

-- 3.7 admin 绑定超级管理员角色（user_type=1 平台员工）
INSERT IGNORE INTO t_sys_user_role (user_type, user_id, role_id) VALUES
(1, 1, 1);

-- ============================================================
-- 迁移完成。校验：
--   SELECT COUNT(*) FROM t_user;          -- 存量会员应保持不变（已去除 admin）
--   SHOW COLUMNS FROM t_user LIKE 'role'; -- 应为空
--   SELECT * FROM t_sys_user; SELECT * FROM t_sys_menu;
-- ============================================================
