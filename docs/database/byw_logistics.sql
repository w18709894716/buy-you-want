CREATE DATABASE IF NOT EXISTS byw_logistics DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE byw_logistics;

DROP TABLE IF EXISTS t_logistics_order;
CREATE TABLE t_logistics_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    company_code VARCHAR(50),
    company_name VARCHAR(100),
    tracking_no VARCHAR(100),
    sender_name VARCHAR(50),
    sender_phone VARCHAR(20),
    sender_address VARCHAR(300),
    receiver_name VARCHAR(50),
    receiver_phone VARCHAR(20),
    receiver_address VARCHAR(300),
    status TINYINT DEFAULT 0 COMMENT '0已揽收 1运输中 2派送中 3已签收',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_order_no (order_no),
    INDEX idx_tracking_no (tracking_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS t_logistics_trace;
CREATE TABLE t_logistics_trace (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    logistics_id BIGINT NOT NULL,
    tracking_no VARCHAR(100),
    description VARCHAR(500),
    location VARCHAR(200),
    trace_time DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_logistics_id (logistics_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========== 清空数据 ==========
TRUNCATE TABLE t_logistics_trace;
TRUNCATE TABLE t_logistics_order;

-- ========== 初始化数据 ==========

-- 物流订单
INSERT INTO t_logistics_order (order_no, company_code, company_name, tracking_no, sender_name, sender_phone, sender_address, receiver_name, receiver_phone, receiver_address, status, created_at) VALUES
('ORD202606010001', 'SF', '顺丰速运', 'SF1234567890', '数码旗舰店', '400-811-1111', '广东省深圳市南山区科技园', '张三', '13800001111', '北京市朝阳区三里屯街道建国路88号', 3, '2026-06-02 09:00:00'),
('ORD202606020001', 'JD', '京东物流', 'JD9876543210', 'Apple自营店', '400-606-5555', '上海市浦东新区机场镇', '李四', '13800002222', '广东省深圳市南山区科技园街道科苑路10号', 3, '2026-06-03 10:00:00'),
('ORD202606050001', 'YTO', '圆通速递', 'YT2024060500001', 'Nike官方店', '400-888-8888', '福建省厦门市湖里区', '张三', '13800001111', '上海市浦东新区陆家嘴街道世纪大道100号', 2, '2026-06-06 14:00:00'),
('ORD202606130001', 'ZTO', '中通快递', 'ZT2024061300001', 'Apple授权店', '400-888-9999', '北京市西城区', '孙七', '13800005555', '四川省成都市武侯区天府大道999号', 2, '2026-06-14 10:00:00');

-- 物流轨迹
INSERT INTO t_logistics_trace (logistics_id, tracking_no, description, location, trace_time, created_at) VALUES
-- 顺丰物流轨迹
(1, 'SF1234567890', '已签收，感谢使用顺丰', '北京市朝阳区', '2026-06-04 18:00:00', '2026-06-04 18:00:00'),
(1, 'SF1234567890', '快件正在派送中，请保持电话畅通', '北京市朝阳区', '2026-06-04 09:00:00', '2026-06-04 09:00:00'),
(1, 'SF1234567890', '快件已到达北京转运中心', '北京', '2026-06-03 22:00:00', '2026-06-03 22:00:00'),
(1, 'SF1234567890', '快件已从深圳发出', '深圳', '2026-06-02 15:00:00', '2026-06-02 15:00:00'),
(1, 'SF1234567890', '顺丰速运已收件', '深圳', '2026-06-02 10:00:00', '2026-06-02 10:00:00'),
-- 京东物流轨迹
(2, 'JD9876543210', '已签收，感谢使用京东物流', '深圳市南山区', '2026-06-05 15:00:00', '2026-06-05 15:00:00'),
(2, 'JD9876543210', '快件正在派送中', '深圳市南山区', '2026-06-05 10:00:00', '2026-06-05 10:00:00'),
(2, 'JD9876543210', '快件已到达深圳集散中心', '深圳', '2026-06-04 08:00:00', '2026-06-04 08:00:00'),
(2, 'JD9876543210', '快件已从上海发出', '上海', '2026-06-03 18:00:00', '2026-06-03 18:00:00'),
(2, 'JD9876543210', '京东物流已收件', '上海', '2026-06-03 12:00:00', '2026-06-03 12:00:00'),
-- 圆通物流轨迹
(3, 'YT2024060500001', '快件正在派送中，请保持电话畅通', '上海市浦东新区', '2026-06-09 09:00:00', '2026-06-09 09:00:00'),
(3, 'YT2024060500001', '快件已到达上海转运中心', '上海', '2026-06-08 20:00:00', '2026-06-08 20:00:00'),
(3, 'YT2024060500001', '快件已从厦门发出', '厦门', '2026-06-07 10:00:00', '2026-06-07 10:00:00'),
(3, 'YT2024060500001', '圆通速递已收件', '厦门', '2026-06-06 16:00:00', '2026-06-06 16:00:00'),
-- 中通物流轨迹
(4, 'ZT2024061300001', '快件正在派送中', '成都市武侯区', '2026-06-16 09:00:00', '2026-06-16 09:00:00'),
(4, 'ZT2024061300001', '快件已到达成都转运中心', '成都', '2026-06-15 22:00:00', '2026-06-15 22:00:00'),
(4, 'ZT2024061300001', '快件已从北京发出', '北京', '2026-06-14 18:00:00', '2026-06-14 18:00:00'),
(4, 'ZT2024061300001', '中通快递已收件', '北京', '2026-06-14 12:00:00', '2026-06-14 12:00:00');
