package com.byw.logistics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.logistics.dto.LogisticsDTO;
import com.byw.api.logistics.dto.ShipRequestDTO;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.result.PageResult;
import com.byw.logistics.entity.LogisticsOrder;
import com.byw.logistics.entity.LogisticsTrace;
import com.byw.logistics.mapper.LogisticsOrderMapper;
import com.byw.logistics.mapper.LogisticsTraceMapper;
import com.byw.logistics.producer.LogisticsEventProducer;
import com.byw.logistics.service.LogisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogisticsServiceImpl implements LogisticsService {

    private final LogisticsOrderMapper logisticsOrderMapper;
    private final LogisticsTraceMapper logisticsTraceMapper;
    private final LogisticsEventProducer logisticsEventProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LogisticsDTO ship(ShipRequestDTO request) {
        // 1. 创建物流订单
        LogisticsOrder order = new LogisticsOrder();
        order.setOrderNo(request.getOrderNo());
        order.setCompanyCode(request.getCompanyCode());
        order.setCompanyName(request.getCompanyName());
        // 运单号：优先使用传入值，为空时自动生成
        String trackingNo = (request.getTrackingNo() != null && !request.getTrackingNo().isBlank())
                ? request.getTrackingNo()
                : generateTrackingNo(request.getCompanyCode());
        order.setTrackingNo(trackingNo);
        order.setSenderName(request.getSenderName());
        order.setSenderPhone(request.getSenderPhone());
        order.setSenderAddress(request.getSenderAddress());
        order.setReceiverName(request.getReceiverName());
        order.setReceiverPhone(request.getReceiverPhone());
        order.setReceiverAddress(request.getReceiverAddress());
        order.setStatus(0); // 已揽收
        logisticsOrderMapper.insert(order);

        // 2. 创建初始物流轨迹
        LogisticsTrace trace = new LogisticsTrace();
        trace.setLogisticsId(order.getId());
        trace.setTrackingNo(order.getTrackingNo());
        trace.setDescription("包裹已揽收");
        trace.setLocation(request.getSenderAddress());
        trace.setTraceTime(LocalDateTime.now());
        logisticsTraceMapper.insert(trace);

        // 3. 发送物流更新事件
        logisticsEventProducer.sendLogisticsUpdateEvent(order.getOrderNo(), order.getTrackingNo(), "SHIPPED");

        log.info("发货成功: orderNo={}, trackingNo={}", order.getOrderNo(), order.getTrackingNo());

        // 4. 返回物流信息
        return buildLogisticsDTO(order, List.of(trace));
    }

    @Override
    public LogisticsDTO track(String orderNo) {
        // 1. 查询物流订单（一单多包裹时取最新一条）
        LogisticsOrder order = logisticsOrderMapper.selectOne(
                new LambdaQueryWrapper<LogisticsOrder>()
                        .eq(LogisticsOrder::getOrderNo, orderNo)
                        .orderByDesc(LogisticsOrder::getCreatedAt)
                        .last("LIMIT 1"));
        if (order == null) {
            throw new BusinessException("物流信息不存在");
        }

        // 2. 查询轨迹
        List<LogisticsTrace> traces = logisticsTraceMapper.selectList(
                new LambdaQueryWrapper<LogisticsTrace>()
                        .eq(LogisticsTrace::getLogisticsId, order.getId())
                        .orderByDesc(LogisticsTrace::getTraceTime));

        return buildLogisticsDTO(order, traces);
    }

    @Override
    public List<LogisticsDTO> trackAll(String orderNo) {
        // 一单多包裹：返回该订单全部物流包裹，各自附轨迹
        List<LogisticsOrder> orders = logisticsOrderMapper.selectList(
                new LambdaQueryWrapper<LogisticsOrder>()
                        .eq(LogisticsOrder::getOrderNo, orderNo)
                        .orderByAsc(LogisticsOrder::getCreatedAt));
        return orders.stream().map(order -> {
            List<LogisticsTrace> traces = logisticsTraceMapper.selectList(
                    new LambdaQueryWrapper<LogisticsTrace>()
                            .eq(LogisticsTrace::getLogisticsId, order.getId())
                            .orderByDesc(LogisticsTrace::getTraceTime));
            return buildLogisticsDTO(order, traces);
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTrace(Long logisticsId, LogisticsTrace trace) {
        // 1. 查询物流订单
        LogisticsOrder order = logisticsOrderMapper.selectById(logisticsId);
        if (order == null) {
            throw new BusinessException("物流订单不存在");
        }

        // 2. 添加轨迹
        trace.setLogisticsId(logisticsId);
        trace.setTrackingNo(order.getTrackingNo());
        trace.setTraceTime(LocalDateTime.now());
        logisticsTraceMapper.insert(trace);

        // 3. 更新物流状态（根据描述判断）
        Integer newStatus = inferStatus(trace.getDescription());
        if (newStatus != null && newStatus > order.getStatus()) {
            order.setStatus(newStatus);
            logisticsOrderMapper.updateById(order);
        }

        // 4. 发送物流更新事件
        logisticsEventProducer.sendLogisticsUpdateEvent(order.getOrderNo(), order.getTrackingNo(), "TRACE_UPDATE");

        log.info("物流轨迹更新: logisticsId={}, description={}", logisticsId, trace.getDescription());
    }

    // ==================== 私有方法 ====================

    private LogisticsDTO buildLogisticsDTO(LogisticsOrder order, List<LogisticsTrace> traces) {
        LogisticsDTO dto = new LogisticsDTO();
        dto.setId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setCompanyCode(order.getCompanyCode());
        dto.setCompanyName(order.getCompanyName());
        dto.setTrackingNo(order.getTrackingNo());
        dto.setStatus(order.getStatus());

        List<LogisticsDTO.TraceDTO> traceDTOs = traces.stream().map(t -> {
            LogisticsDTO.TraceDTO traceDTO = new LogisticsDTO.TraceDTO();
            BeanUtils.copyProperties(t, traceDTO);
            return traceDTO;
        }).collect(Collectors.toList());
        dto.setTraces(traceDTOs);

        return dto;
    }

    private String generateTrackingNo(String companyCode) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return (companyCode != null ? companyCode.substring(0, Math.min(companyCode.length(), 4)) : "EXPS") + suffix;
    }

    private Integer inferStatus(String description) {
        if (description == null) return null;
        if (description.contains("签收")) return 3;
        if (description.contains("派送") || description.contains("派件")) return 2;
        if (description.contains("运输") || description.contains("到达") || description.contains("转运")) return 1;
        return null;
    }

    @Override
    public PageResult<LogisticsOrder> adminListLogistics(Integer pageNum, Integer pageSize, String orderNo, Integer status) {
        LambdaQueryWrapper<LogisticsOrder> wrapper = new LambdaQueryWrapper<LogisticsOrder>()
                .like(orderNo != null && !orderNo.isEmpty(), LogisticsOrder::getOrderNo, orderNo)
                .eq(status != null, LogisticsOrder::getStatus, status)
                .orderByDesc(LogisticsOrder::getCreatedAt);
        Page<LogisticsOrder> page = new Page<>(pageNum, pageSize);
        Page<LogisticsOrder> resultPage = logisticsOrderMapper.selectPage(page, wrapper);
        return PageResult.of(resultPage.getRecords(), resultPage.getTotal(), pageNum, pageSize);
    }

    @Override
    public List<LogisticsTrace> adminGetTrace(Long logisticsId) {
        return logisticsTraceMapper.selectList(
                new LambdaQueryWrapper<LogisticsTrace>()
                        .eq(LogisticsTrace::getLogisticsId, logisticsId)
                        .orderByDesc(LogisticsTrace::getTraceTime));
    }
}
