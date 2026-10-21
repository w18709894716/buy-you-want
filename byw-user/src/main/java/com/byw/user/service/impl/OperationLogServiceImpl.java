package com.byw.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.user.dto.OperationLogDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.log.dto.OperationLogMessage;
import com.byw.user.entity.OperationLog;
import com.byw.user.mapper.OperationLogMapper;
import com.byw.user.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    @Override
    public void save(OperationLogMessage message) {
        OperationLog entity = new OperationLog();
        entity.setModule(message.getModule());
        entity.setOperation(message.getOperation());
        entity.setDescription(message.getDescription());
        entity.setOperatorId(message.getOperatorId());
        entity.setOperatorType(message.getOperatorType());
        entity.setOperatorName(message.getOperatorName());
        entity.setMethod(message.getMethod());
        entity.setRequestUri(message.getRequestUri());
        entity.setRequestMethod(message.getRequestMethod());
        entity.setClientIp(message.getClientIp());
        entity.setSuccess(message.isSuccess() ? 1 : 0);
        entity.setErrorMsg(message.getErrorMsg());
        entity.setCostMs(message.getCostMs());
        entity.setTraceId(message.getTraceId());
        operationLogMapper.insert(entity);
    }

    @Override
    public PageResult<OperationLogDTO> page(int pageNum, int pageSize, String module, Long operatorId,
                                            Integer success, String startTime, String endTime) {
        LocalDateTime from = parseTime(startTime);
        LocalDateTime to = parseTime(endTime);
        Page<OperationLog> page = operationLogMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<OperationLog>()
                        .eq(module != null && !module.isBlank(), OperationLog::getModule, module)
                        .eq(operatorId != null, OperationLog::getOperatorId, operatorId)
                        .eq(success != null, OperationLog::getSuccess, success)
                        .ge(from != null, OperationLog::getCreatedAt, from)
                        .le(to != null, OperationLog::getCreatedAt, to)
                        .orderByDesc(OperationLog::getId));
        List<OperationLogDTO> records = page.getRecords().stream().map(this::toDto).toList();
        return PageResult.of(records, page.getTotal(), pageNum, pageSize);
    }

    private OperationLogDTO toDto(OperationLog entity) {
        OperationLogDTO dto = new OperationLogDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    /** 兼容 "2026-10-16 10:00:00" 与 ISO 两种写法；解析不了就忽略该条件（不报 500） */
    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim().replace(' ', 'T'));
        } catch (DateTimeParseException e) {
            log.warn("操作日志查询时间格式无法解析，已忽略该条件: {}", value);
            return null;
        }
    }
}
