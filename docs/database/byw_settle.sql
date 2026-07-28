CREATE DATABASE IF NOT EXISTS byw_settle DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE byw_settle;

-- 佣金规则（按商品分类配置佣金率；category_id=0 为平台默认兜底规则）
DROP TABLE IF EXISTS t_commission_rule;
CREATE TABLE t_commission_rule (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_id     BIGINT NOT NULL DEFAULT 0 COMMENT '商品分类ID(0=平台默认兜底规则)',
    category_name   VARCHAR(100) COMMENT '分类名称',
    commission_rate DECIMAL(6,4) NOT NULL DEFAULT 0.0500 COMMENT '佣金率(0~1小数,如0.0500=5%)',
    enabled         TINYINT DEFAULT 1 COMMENT '0停用 1启用',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT DEFAULT 0,
    UNIQUE KEY uk_category (category_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 结算单（每个已收货子订单一条）
DROP TABLE IF EXISTS t_settle_record;
CREATE TABLE t_settle_record (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    settle_no         VARCHAR(64) NOT NULL UNIQUE COMMENT '结算单号',
    order_no          VARCHAR(64) NOT NULL COMMENT '子订单号',
    parent_order_no   VARCHAR(64) COMMENT '父订单号',
    shop_id           BIGINT NOT NULL COMMENT '归属店铺ID',
    user_id           BIGINT COMMENT '下单用户ID',
    order_amount      DECIMAL(10,2) NOT NULL COMMENT '订单实付(结算基数)',
    commission_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '平台佣金',
    settle_amount     DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '商家应得',
    status            TINYINT NOT NULL DEFAULT 0 COMMENT '0待结算(冷静期冻结) 1已入账 2已关闭(退款)',
    receive_time      DATETIME COMMENT '收货时间',
    expect_settle_time DATETIME COMMENT '预计入账时间(收货+T+N)',
    settle_time       DATETIME COMMENT '实际入账时间',
    remark            VARCHAR(200),
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted           TINYINT DEFAULT 0,
    UNIQUE KEY uk_order_no (order_no, deleted),
    INDEX idx_shop_id (shop_id),
    INDEX idx_status (status),
    INDEX idx_expect_settle_time (expect_settle_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 商家余额账户（每店铺一条）
DROP TABLE IF EXISTS t_shop_balance;
CREATE TABLE t_shop_balance (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_id           BIGINT NOT NULL COMMENT '归属店铺ID',
    total_income      DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计确认收入',
    pending_amount    DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '待结算金额(冷静期)',
    available_balance DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '可提现余额',
    frozen_amount     DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '提现冻结中金额',
    withdrawn_amount  DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计已提现',
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted           TINYINT DEFAULT 0,
    UNIQUE KEY uk_shop_id (shop_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 余额流水（每笔资金变动一条）
DROP TABLE IF EXISTS t_balance_flow;
CREATE TABLE t_balance_flow (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    flow_no       VARCHAR(64) NOT NULL UNIQUE COMMENT '流水号',
    shop_id       BIGINT NOT NULL COMMENT '归属店铺ID',
    type          TINYINT NOT NULL COMMENT '1结算待入账 2结算入账 3提现冻结 4提现成功 5提现驳回解冻',
    amount        DECIMAL(12,2) NOT NULL COMMENT '变动金额(正入账/负出账)',
    balance_after DECIMAL(12,2) COMMENT '变动后可用余额',
    ref_no        VARCHAR(64) COMMENT '关联单号(结算单号/提现单号)',
    remark        VARCHAR(200),
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_shop_id (shop_id),
    INDEX idx_ref_no (ref_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 提现单
DROP TABLE IF EXISTS t_withdraw_record;
CREATE TABLE t_withdraw_record (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    withdraw_no   VARCHAR(64) NOT NULL UNIQUE COMMENT '提现单号',
    shop_id       BIGINT NOT NULL COMMENT '归属店铺ID',
    amount        DECIMAL(10,2) NOT NULL COMMENT '提现金额',
    status        TINYINT NOT NULL DEFAULT 0 COMMENT '0待审核 1通过(已打款) 2驳回',
    account_type  VARCHAR(20) COMMENT '收款账户类型 bank/alipay/wechat',
    account_no    VARCHAR(100) COMMENT '收款账号',
    account_name  VARCHAR(50) COMMENT '收款人姓名',
    apply_time    DATETIME COMMENT '申请时间',
    audit_time    DATETIME COMMENT '审核时间',
    auditor       VARCHAR(50) COMMENT '审核人',
    reject_reason VARCHAR(200) COMMENT '驳回原因',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT DEFAULT 0,
    INDEX idx_shop_id (shop_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== 初始数据 ==========
-- 平台默认兜底佣金率 5%
INSERT INTO t_commission_rule (category_id, category_name, commission_rate, enabled)
VALUES (0, '平台默认', 0.0500, 1);
