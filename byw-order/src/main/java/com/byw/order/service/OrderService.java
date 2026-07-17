package com.byw.order.service;

import com.byw.api.order.dto.OrderCreateDTO;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.common.core.result.PageResult;

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
    PageResult<OrderDetailDTO> getUserOrders(Long userId, Integer status, Integer pageNum, Integer pageSize);

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
     * 获取用户各状态订单数量
     * @return Map<状态码, 数量>
     */
    java.util.Map<Integer, Integer> getOrderCountsByStatus(Long userId);

    /**
     * 更新订单评价状态
     */
    void updateReviewed(String orderNo, Integer reviewed);
}
