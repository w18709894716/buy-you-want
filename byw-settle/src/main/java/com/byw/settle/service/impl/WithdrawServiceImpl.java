package com.byw.settle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.settle.dto.WithdrawApplyDTO;
import com.byw.api.settle.dto.WithdrawRecordDTO;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.result.PageResult;
import com.byw.settle.entity.ShopBalance;
import com.byw.settle.entity.WithdrawRecord;
import com.byw.settle.mapper.WithdrawRecordMapper;
import com.byw.settle.service.BalanceService;
import com.byw.settle.service.ShopNameResolver;
import com.byw.settle.service.WithdrawService;
import com.byw.settle.util.NoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawServiceImpl implements WithdrawService {

    private final WithdrawRecordMapper withdrawRecordMapper;
    private final BalanceService balanceService;
    private final ShopNameResolver shopNameResolver;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String applyWithdraw(Long shopId, WithdrawApplyDTO applyDTO) {
        if (shopId == null) {
            throw new BusinessException("无法识别商家身份");
        }
        BigDecimal amount = applyDTO.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("提现金额必须大于0");
        }
        ShopBalance balance = balanceService.getOrCreate(shopId);
        if (balance.getAvailableBalance().compareTo(amount) < 0) {
            throw new BusinessException("可提现余额不足");
        }

        // 冻结可用余额
        String withdrawNo = NoGenerator.withdrawNo();
        balanceService.freezeForWithdraw(shopId, amount, withdrawNo, "提现冻结");

        WithdrawRecord record = new WithdrawRecord();
        record.setWithdrawNo(withdrawNo);
        record.setShopId(shopId);
        record.setAmount(amount);
        record.setStatus(0);
        record.setAccountType(applyDTO.getAccountType());
        record.setAccountNo(applyDTO.getAccountNo());
        record.setAccountName(applyDTO.getAccountName());
        record.setApplyTime(LocalDateTime.now());
        withdrawRecordMapper.insert(record);
        log.info("商家发起提现: withdrawNo={}, shopId={}, amount={}", withdrawNo, shopId, amount);
        return withdrawNo;
    }

    @Override
    public PageResult<WithdrawRecordDTO> listWithdraws(Long shopId, Integer status, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<WithdrawRecord> wrapper = new LambdaQueryWrapper<WithdrawRecord>()
                .eq(shopId != null, WithdrawRecord::getShopId, shopId)
                .eq(status != null, WithdrawRecord::getStatus, status)
                .orderByDesc(WithdrawRecord::getCreatedAt);
        IPage<WithdrawRecord> page = withdrawRecordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        List<Long> shopIds = page.getRecords().stream().map(WithdrawRecord::getShopId).collect(Collectors.toList());
        Map<Long, String> shopNames = shopNameResolver.resolve(shopIds);

        List<WithdrawRecordDTO> list = page.getRecords().stream().map(r -> {
            WithdrawRecordDTO dto = new WithdrawRecordDTO();
            BeanUtils.copyProperties(r, dto);
            dto.setShopName(shopNames.get(r.getShopId()));
            return dto;
        }).collect(Collectors.toList());
        return PageResult.of(list, page.getTotal(), pageNum, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean auditWithdraw(Long withdrawId, Boolean pass, String rejectReason, String auditor) {
        WithdrawRecord record = withdrawRecordMapper.selectById(withdrawId);
        if (record == null) {
            throw new BusinessException("提现单不存在");
        }
        if (record.getStatus() != 0) {
            throw new BusinessException("提现单已审批，不可重复操作");
        }

        boolean approved = Boolean.TRUE.equals(pass);
        record.setStatus(approved ? 1 : 2);
        record.setAuditTime(LocalDateTime.now());
        record.setAuditor(auditor);
        record.setRejectReason(approved ? null : rejectReason);
        withdrawRecordMapper.updateById(record);

        if (approved) {
            balanceService.withdrawSuccess(record.getShopId(), record.getAmount(),
                    record.getWithdrawNo(), "提现审核通过打款");
        } else {
            balanceService.unfreezeForReject(record.getShopId(), record.getAmount(),
                    record.getWithdrawNo(), "提现驳回解冻");
        }
        log.info("提现审批: withdrawNo={}, pass={}", record.getWithdrawNo(), approved);
        return true;
    }
}
