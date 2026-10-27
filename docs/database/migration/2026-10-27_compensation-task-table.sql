-- =============================================================================
-- 迁移脚本：新增后置补偿任务表（本地消息表）
-- 日期：2026-10-27
-- 对应复审结论：第三批遗留「资金动作在事务提交后 best-effort 调用，失败只打日志，
--              本地没有任何标记可对账」，见 docs/architecture/review-2026-10-14.md
--
-- 背景：
--   取消订单退款、售后退款冲减结算、下单失败撤券/撤预占这四类动作，原来都是
--   "本地事务提交后调远端，失败只 log.error"。进程若在「本地提交成功、远端调用之前/之中」
--   崩溃，就会出现：钱收了单没退、或商家结算没被冲减，而本地状态与"处理成功"完全一样，
--   没有任何扫描任务能发现（售后卡单能扫，是因为它停在「退款中」这个状态上）。
--
-- 方案：把待办动作落成一张独立的表，随业务数据一起在**同一个本地事务**里提交。
--   事务提交 ⇒ 任务必定入库；任务由定时器驱动，失败按指数退避重试，超次数转"需人工"。
--   这一版不引入 MQ：动作都是出站 Feign 调用，本地消息表足够，且失败在表里可见可 join。
--
-- 幂等性说明（决定它能不能被安全重试）：
--   CANCEL_REFUND   → pay 侧按 refNo = CANCEL:{orderNo} 去重
--   SETTLE_ADJUST   → 结算侧按 afterSaleNo + t_balance_flow.ref_no 查重
--   RELEASE_COUPON  → 只把 status=1 的领券记录翻回 0，重复执行是 no-op
--   RELEASE_STOCK   → CAS(lock_stock >= qty)，重复执行会报错但不会多退库存
--   （「未发货退款回补库存」restoreStock 是 stock = stock + q，重复执行会虚增库存，
--     因此**没有**纳入本表，仍保持 best-effort + 人工补。）
--
-- 执行说明：CREATE TABLE IF NOT EXISTS，可重复执行。已有库执行一次即可，新库由
--           docs/database/byw_order.sql 直接建表，不需要跑本脚本。
-- =============================================================================

USE byw_order;

CREATE TABLE IF NOT EXISTS t_compensation_task (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    task_type     VARCHAR(32)  NOT NULL COMMENT '补偿类型 CANCEL_REFUND/SETTLE_ADJUST/RELEASE_COUPON/RELEASE_STOCK',
    biz_key       VARCHAR(96)  NOT NULL COMMENT '幂等键：CANCEL:{orderNo} / afterSaleNo / {orderNo}:{couponId} / parentOrderNo',
    order_no      VARCHAR(64)  DEFAULT NULL,
    after_sale_no VARCHAR(64)  DEFAULT NULL,
    payload       TEXT         COMMENT '执行参数 JSON 快照，执行时不再回查业务表（回补/撤预占可能几十个 SKU，故用 TEXT）',
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0待执行 1已完成 2需人工 3执行中',
    retry_count   INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    next_retry_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次可执行时间（指数退避）',
    last_error    VARCHAR(500) DEFAULT NULL COMMENT '最近一次失败原因，供人工排查',
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_type_biz (task_type, biz_key),
    KEY idx_status_next (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后置补偿任务（本地消息表）';

-- 验证：应返回 1
SELECT COUNT(*) AS t_compensation_task_exists
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 't_compensation_task';

-- 验证：唯一键与扫描索引各应返回 1（这里用 COUNT(DISTINCT INDEX_NAME)，
-- 因为 information_schema.STATISTICS 是"每个索引列一行"）
SELECT COUNT(DISTINCT INDEX_NAME) AS index_count
  FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA = DATABASE()
   AND TABLE_NAME = 't_compensation_task'
   AND INDEX_NAME IN ('uk_type_biz', 'idx_status_next');

-- =============================================================================
-- 回滚
-- =============================================================================
-- 注意：DROP 会丢掉仍在待执行的补偿任务。回滚前先确认没有 status IN (0, 3) 的行：
--   SELECT * FROM t_compensation_task WHERE status IN (0, 3);
-- DROP TABLE t_compensation_task;
