-- =============================================
-- 增量迁移：支付单存量数据回填
-- 背景：C端支付页此前未走 byw-pay（仅调 /order/pay 模拟改状态），
--       导致已支付订单在 t_pay_order 中无支付单记录，售后退款时报"未找到已支付的订单"。
--       前端已改为真实链路（/pay/create + /pay/callback），本脚本为存量已支付订单补支付单。
-- 说明：支付发起在父订单（is_parent=1）或独立订单（无父单，如秒杀单），按此口径回填；
--       已退款关闭的订单（status=4 且 close_type=2）支付单置为已退款(3)，其余置为已支付(1)。
-- 执行方式：mysql -u root -p < migration_pay_order_backfill.sql
-- =============================================

INSERT INTO byw_pay.t_pay_order
    (pay_no, order_no, user_id, amount, pay_channel, status, channel_trade_no, pay_time, created_at, updated_at, deleted)
SELECT
    CONCAT('PAY', DATE_FORMAT(o.pay_time, '%Y%m%d%H%i%s'), LPAD(o.id, 4, '0')),
    o.order_no,
    o.user_id,
    o.pay_amount,
    'wechat',
    CASE WHEN o.status = 4 AND o.close_type = 2 THEN 3 ELSE 1 END,
    CONCAT('MOCKBF', o.id),
    o.pay_time,
    o.pay_time,
    NOW(),
    0
FROM byw_order.t_order o
WHERE o.pay_time IS NOT NULL
  AND (o.is_parent = 1 OR o.parent_order_no IS NULL)
  AND o.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM byw_pay.t_pay_order p
      WHERE p.order_no = o.order_no AND p.deleted = 0
  );
