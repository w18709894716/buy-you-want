package com.byw.settle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.byw.settle.entity.SettleRecord;
import com.byw.settle.mapper.SettleRecordMapper;
import com.byw.settle.service.BalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 结算入账事务单元：单条结算单入账（状态流转 + 余额变动）在同一事务内完成。
 * 独立 Bean 以保证 @Transactional 通过代理生效（避免同类自调用失效）。
 */
@Component
@RequiredArgsConstructor
public class SettleAccounting {

    private final SettleRecordMapper settleRecordMapper;
    private final BalanceService balanceService;

    /** 将单条到期结算单转入可用余额；乐观更新防并发重复入账。返回是否入账成功。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean settleOne(SettleRecord record) {
        SettleRecord update = new SettleRecord();
        update.setId(record.getId());
        update.setStatus(1);
        update.setSettleTime(LocalDateTime.now());
        int rows = settleRecordMapper.update(update, new LambdaQueryWrapper<SettleRecord>()
                .eq(SettleRecord::getId, record.getId())
                .eq(SettleRecord::getStatus, 0));
        if (rows == 0) {
            return false;
        }
        balanceService.settleToAvailable(record.getShopId(), record.getSettleAmount(),
                record.getSettleNo(), "订单结算入账");
        return true;
    }
}
