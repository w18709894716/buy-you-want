-- byw_shop database
CREATE DATABASE IF NOT EXISTS byw_shop DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE byw_shop;

-- 店铺表
DROP TABLE IF EXISTS t_shop;
CREATE TABLE t_shop (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL COMMENT '店铺名称',
    logo            VARCHAR(500) COMMENT '店铺Logo',
    description     VARCHAR(500) COMMENT '店铺简介',
    merchant_id     BIGINT COMMENT '归属商家账号ID',
    contact_name    VARCHAR(50) COMMENT '联系人',
    contact_phone   VARCHAR(20) COMMENT '联系电话',
    self_operated   TINYINT DEFAULT 1 COMMENT '0自营 1第三方商家',
    status          TINYINT DEFAULT 1 COMMENT '0关店 1营业 2封禁',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT DEFAULT 0,
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 商家账号（入驻申请与商家登录主体）
DROP TABLE IF EXISTS t_merchant_account;
CREATE TABLE t_merchant_account (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    username        VARCHAR(50) NOT NULL UNIQUE COMMENT '登录用户名',
    password        VARCHAR(200) NOT NULL COMMENT '密码BCrypt',
    real_name       VARCHAR(50) COMMENT '商家真实姓名/企业联系人',
    phone           VARCHAR(20) COMMENT '联系电话',
    merchant_type   TINYINT DEFAULT 1 COMMENT '入驻类型 1个人 2企业',
    shop_name       VARCHAR(100) COMMENT '意向店铺名称（审核通过后用于建店）',
    company_name    VARCHAR(128) COMMENT '企业名称（企业入驻）',
    id_card_front   VARCHAR(500) COMMENT '身份证人像面图片URL（个人入驻）',
    id_card_back    VARCHAR(500) COMMENT '身份证国徽面图片URL（个人入驻）',
    business_license VARCHAR(500) COMMENT '营业执照图片URL（企业入驻）',
    agreement_signed TINYINT DEFAULT 0 COMMENT '已签署入驻协议 0否 1是',
    apply_user_id   BIGINT COMMENT '发起申请的C端用户ID',
    shop_id         BIGINT COMMENT '关联店铺ID(审核通过后回填)',
    role            VARCHAR(30) DEFAULT 'merchant_owner' COMMENT 'merchant_owner/merchant_staff',
    audit_status    TINYINT DEFAULT 0 COMMENT '0待审核 1通过 2驳回',
    reject_reason   VARCHAR(255) COMMENT '驳回原因',
    status          TINYINT DEFAULT 0 COMMENT '0禁用 1正常',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT DEFAULT 0,
    INDEX idx_username (username),
    INDEX idx_shop_id (shop_id),
    INDEX idx_audit_status (audit_status),
    INDEX idx_apply_user_id (apply_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== 初始数据 ==========
-- 默认自营店铺（id=1），存量商品/订单/优惠券/评价的 shop_id 统一回填为 1
INSERT INTO t_shop (id, name, description, merchant_id, contact_name, self_operated, status)
VALUES (1, '官方自营旗舰店', '平台自营店铺', NULL, '平台运营', 0, 1);

-- 示例第三方商家账号（demo_merchant / admin123），已审核通过并绑定示例店铺
INSERT INTO t_shop (id, name, description, merchant_id, contact_name, contact_phone, self_operated, status)
VALUES (2, '优选数码专营店', '第三方入驻数码商家', 1, '王老板', '13900000001', 1, 1);

INSERT INTO t_merchant_account (id, username, password, real_name, phone, shop_id, role, audit_status, status)
VALUES (1, 'demo_merchant', '$2a$10$mG4HpWhYdqOSYql91nc17OrmYxpwkchw/0Vbs5oR.txUEHBmEiVem', '王老板', '13900000001', 2, 'merchant_owner', 1, 1);
