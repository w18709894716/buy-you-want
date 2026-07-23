package com.byw.logistics.service;

import com.byw.api.logistics.dto.LogisticsDTO;
import com.byw.api.logistics.dto.ShipRequestDTO;
import com.byw.common.core.result.PageResult;
import com.byw.logistics.entity.LogisticsOrder;
import com.byw.logistics.entity.LogisticsTrace;

public interface LogisticsService {

    /**
     * 发货
     * @return 物流信息
     */
    LogisticsDTO ship(ShipRequestDTO request);

    /**
     * 查询物流轨迹
     * @param orderNo 订单编号
     * @return 物流信息（含轨迹）
     */
    LogisticsDTO track(String orderNo);

    /**
     * 查询订单全部物流包裹（一单多包裹）
     * @param orderNo 订单编号
     * @return 物流包裹列表（各含轨迹）
     */
    java.util.List<LogisticsDTO> trackAll(String orderNo);

    /**
     * 更新物流轨迹
     * @param logisticsId 物流单ID
     * @param trace 轨迹信息
     */
    void updateTrace(Long logisticsId, LogisticsTrace trace);

    /**
     * 管理端：获取物流列表
     */
    PageResult<LogisticsOrder> adminListLogistics(Integer pageNum, Integer pageSize, String orderNo, Integer status);

    /**
     * 管理端：获取物流轨迹
     */
    java.util.List<LogisticsTrace> adminGetTrace(Long logisticsId);
}
