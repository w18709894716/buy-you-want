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
     * 生成订单号
     */
    String generateOrderNo();
}
