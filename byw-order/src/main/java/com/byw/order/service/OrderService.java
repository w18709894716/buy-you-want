package com.byw.order.service;

import com.byw.api.order.dto.OrderCreateDTO;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.common.core.result.PageResult;

import java.util.List;

public interface OrderService {

    /**
     * 创建订单
     * @return 订单编号
     */
    String createOrder(OrderCreateDTO createDTO);

    /**
     * 获取订单详情
     */
    OrderDetailDTO getOrderDetail(String orderNo);

    /**
     * 获取用户订单列表
     */
    PageResult<OrderDetailDTO> getUserOrders(Long userId, Integer status, Integer reviewed, Integer pageNum, Integer pageSize);

    /**
     * 取消订单
     */
    void cancelOrder(String orderNo, String reason);

    /**
     * 取消订单（指定操作人）
     */
    void cancelOrder(String orderNo, String reason, String operator);

    /**
     * 确认收货
     */
    void confirmReceive(String orderNo);

    /**
     * 更新订单状态
     */
    void updateStatus(String orderNo, Integer status);

    /**
     * 拆分发货：对选中的订单明细发货
     */
    void shipItems(String orderNo, List<Long> itemIds, String companyName, String trackingNo);

    /**
     * 获取用户各状态订单数量
     * @return Map<状态码, 数量>
     */
    java.util.Map<Integer, Integer> getOrderCountsByStatus(Long userId);

    /**
     * 更新订单评价状态
     */
    void updateReviewed(String orderNo, Integer reviewed);

    /**
     * 自动确认收货：将发货超过指定天数仍未确认的待收货订单置为交易完成
     * @param expireDays 发货后自动确认收货的天数
     * @return 本次处理的订单数量
     */
    int autoConfirmReceive(int expireDays);

    /**
     * 查出超过 timeoutMinutes 仍未支付、且未被超时取消消息取消的子订单/独立订单号。
     *
     * <p>作为 RocketMQ 延迟消息的对账兜底：消息丢失时订单会永久停留待付款、库存永久预占。
     * 调用方需通过 Spring 代理逐个调用 {@link #cancelOrder(String, String, String)} 以复用其事务与状态机。
     *
     * @param limit 单轮最多返回多少条，避免积压时一次拉取过多、长时间占用调度锁
     */
    List<String> listTimeoutUnpaidOrderNos(int timeoutMinutes, int limit);

    /**
     * 支付成功但订单已被取消关闭时的原路退款。
     *
     * <p>「支付回调 0→1」与「超时取消 0→4」竞态下，{@link #updateStatus} 会按状态机拒绝
     * 「已关闭 → 待发货」并抛异常，此时钱已收、货不会发。调用方不能把异常继续抛给 MQ 重试 ——
     * 重试耗尽进死信后这笔退款永远不会发生。
     *
     * <p>退款幂等键为 {@code CANCEL:{orderNo}}，与取消链路共用，两条路径都触发也只退一次。
     *
     * @return true 表示订单确为"取消关闭"并已发起退款；false 表示不属于该场景，调用方应按原逻辑处理异常
     */
    boolean refundCancelledOnPaid(String orderNo);

    /**
     * 生成订单号
     */
    String generateOrderNo();
}
