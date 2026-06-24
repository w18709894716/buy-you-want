-- byw_user database
CREATE DATABASE IF NOT EXISTS byw_user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE byw_user;

-- 用户主表
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
    user_level      TINYINT DEFAULT 0 COMMENT '0普通 1银卡 2金卡 3钻石',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT DEFAULT 0,
    INDEX idx_phone (phone),
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 收货地址
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
CREATE TABLE t_user_level (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    level_name      VARCHAR(50) NOT NULL,
    level_code      TINYINT NOT NULL UNIQUE,
    discount_rate   DECIMAL(3,2) DEFAULT 1.00,
    min_points      INT DEFAULT 0,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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

-- 管理员账号 (admin / admin123)
INSERT INTO t_user (username, password, phone, nickname, status, user_level) VALUES
('admin', '$2a$10$mG4HpWhYdqOSYql91nc17OrmYxpwkchw/0Vbs5oR.txUEHBmEiVem', '13800000000', '系统管理员', 1, 3);

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
