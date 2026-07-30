-- 增量迁移：为官方自营旗舰店(shop_id=1)补建商家账号，使平台运营可登录商家端处理自营店订单/售后
-- 幂等可重复执行
USE byw_shop;

-- 1. 补建自营店商家账号（official_shop / admin123，与种子账号统一密码）
INSERT INTO t_merchant_account (username, password, real_name, phone, shop_id, role, audit_status, status)
SELECT 'official_shop', '$2a$10$mG4HpWhYdqOSYql91nc17OrmYxpwkchw/0Vbs5oR.txUEHBmEiVem', '平台运营', '13800000000', 1, 'merchant_owner', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM t_merchant_account WHERE username = 'official_shop');

-- 2. 回填自营店铺的主账号归属
UPDATE t_shop s
JOIN t_merchant_account ma ON ma.username = 'official_shop'
SET s.merchant_id = ma.id
WHERE s.id = 1 AND (s.merchant_id IS NULL OR s.merchant_id <> ma.id);

-- 验证
SELECT id, username, real_name, shop_id, role, audit_status, status FROM t_merchant_account WHERE username = 'official_shop';
SELECT id, name, merchant_id, self_operated FROM t_shop WHERE id = 1;
