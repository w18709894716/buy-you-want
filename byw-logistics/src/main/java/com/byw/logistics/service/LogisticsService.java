package com.byw.logistics.service;

import com.byw.api.logistics.dto.LogisticsDTO;
import com.byw.api.logistics.dto.ShipRequestDTO;
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
     * 更新物流轨迹
     * @param logisticsId 物流单ID
     * @param trace 轨迹信息
     */
    void updateTrace(Long logisticsId, LogisticsTrace trace);
}
