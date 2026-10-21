-- =============================================================================
-- 迁移脚本：操作审计日志（@OperationLog 落库 + 管理端可查）
-- 日期：2026-10-16
--
-- 背景：各服务的 @OperationLog 切面原先只写日志文件，无法在管理端查询。
--       现改为把结构化日志经 RocketMQ 发给 byw-user 落库到 t_operation_log。
--
-- 执行前必读：本脚本只做**新增**（建表 + 新增一条菜单），不改动任何既有数据；
--             若目标库已存在同名表/菜单，请先确认来源。
-- 幂等性：MySQL 5.7 不支持 CREATE TABLE IF NOT EXISTS 之外的所有场景，这里统一用
--         "先判断后执行"的写法，可重复执行。
-- =============================================================================

-- ① 操作审计日志表（存于 byw_user 库：管理域数据都在这里）
CREATE TABLE IF NOT EXISTS t_operation_log (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    module         VARCHAR(50)  NOT NULL COMMENT '模块：商品/订单/售后/营销/权限/用户…',
    operation      VARCHAR(50)  NOT NULL COMMENT '操作：新增/修改/删除/审核/发货…',
    description    VARCHAR(200) COMMENT '操作描述',
    operator_id    BIGINT       COMMENT '操作人ID',
    operator_type  VARCHAR(20)  COMMENT '操作人类型：sys/merchant/user/unknown',
    operator_name  VARCHAR(50)  COMMENT '操作人账号名（尽力填充）',
    method         VARCHAR(200) COMMENT '类名.方法名',
    request_uri    VARCHAR(300) COMMENT '请求地址',
    request_method VARCHAR(10)  COMMENT 'HTTP 方法',
    client_ip      VARCHAR(64)  COMMENT '客户端IP',
    success        TINYINT      NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
    error_msg      VARCHAR(500) COMMENT '失败原因',
    cost_ms        BIGINT       COMMENT '耗时(毫秒)',
    trace_id       VARCHAR(64)  COMMENT '链路ID，便于与日志文件对照',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_created_at (created_at),
    INDEX idx_module (module),
    INDEX idx_operator (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志';

-- ② 管理端菜单：系统管理 → 操作日志（super_admin 走 * 通配，无需再绑定角色）
INSERT INTO t_sys_menu (id, parent_id, menu_name, menu_type, scope, path, perm_code, icon, sort_order)
SELECT 194, 190, '操作日志', 2, 'platform', '/system/operation-log', 'sys:log', 'Document', 94
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM t_sys_menu WHERE id = 194);

-- ③ 验证：表结构 + 菜单
SELECT COUNT(*) AS table_exists FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_operation_log';
SELECT id, menu_name, path, perm_code FROM t_sys_menu WHERE id = 194;

-- =============================================================================
-- 回滚
-- =============================================================================
-- DROP TABLE IF EXISTS t_operation_log;
-- DELETE FROM t_sys_role_menu WHERE menu_id = 194;
-- DELETE FROM t_sys_menu WHERE id = 194;
