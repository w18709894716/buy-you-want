package com.byw.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byw.user.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作审计日志 Mapper（所在包已被 BywUserApplication 的 @MapperScan 覆盖）。
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
