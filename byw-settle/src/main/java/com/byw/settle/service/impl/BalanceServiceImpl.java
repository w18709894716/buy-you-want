package com.byw.settle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.settle.dto.BalanceFlowDTO;
import com.byw.api.settle.dto.ShopBalanceDTO;
import com.byw.common.core.result.PageResult;
import com.byw.settle.entity.BalanceFlow;
import com.byw.settle.entity.ShopBalance;
import com.byw.settle.mapper.BalanceFlowMapper;
import com.byw.settle.mapper.ShopBalanceMapper;
import com.byw.settle.service.BalanceService;
import com.byw.settle.util.NoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceServiceImpl implements BalanceService {

    private final ShopBalanceMapper shopBalanceMapper;
    private final BalanceFlowMapper balanceFlowMapper;

    private static final String[] FLOW_TYPE_DESC = {
            "", "结算待入账", "结算入账", "提现冻结", "提现成功", "提现驳回解冻"
    };

    @Override
    public ShopBalance getOrCreate(Long shopId) {
        ShopBalance balance = shopBalanceMapper.selectOne(
                new LambdaQueryWrapper<ShopBalance>().eq(ShopBalance::getShopId, shopId));
        if (balance == null) {
            balance = new ShopBalance();
            balance.setShopId(shopId);
            balance.setTotalIncome(BigDecimal.ZERO);
            balance.setPendingAmount(BigDecimal.ZERO);
            balance.setAvailableBalance(BigDecimal.ZERO);
            balance.setFrozenAmount(BigDecimal.ZERO);
            balance.setWithdrawnAmount(BigDecimal.ZERO);
            shopBalanceMapper.insert(balance);
        }
        return balance;
    }

    @Override
    public ShopBalanceDTO getBalanceDTO(Long shopId) {
        ShopBalance balance = getOrCreate(shopId);
        ShopBalanceDTO dto = new ShopBalanceDTO();
        BeanUtils.copyProperties(balance, dto);
        return dto;
    }

    @Override
    public PageResult<BalanceFlowDTO> listFlows(Long shopId, Integer pageNum, Integer pageSize) {
        IPage<BalanceFlow> page = balanceFlowMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<BalanceFlow>()
                        .eq(shopId != null, BalanceFlow::getShopId, shopId)
                        .orderByDesc(BalanceFlow::getId));
        List<BalanceFlowDTO> list = page.getRecords().stream().map(f -> {
            BalanceFlowDTO dto = new BalanceFlowDTO();
            BeanUtils.copyProperties(f, dto);
            dto.setTypeDesc(typeDesc(f.getType()));
            return dto;
        }).collect(Collectors.toList());
        return PageResult.of(list, page.getTotal(), pageNum, pageSize);
    }

    private String typeDesc(Integer type) {
        if (type != null && type >= 1 && type < FLOW_TYPE_DESC.length) {
            return FLOW_TYPE_DESC[type];
        }
        return "";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addPending(Long shopId, BigDecimal amount, String refNo, String remark) {
        ShopBalance balance = getOrCreate(shopId);
        balance.setPendingAmount(balance.getPendingAmount().add(amount));
        shopBalanceMapper.updateById(balance);
        writeFlow(shopId, 1, amount, balance.getAvailableBalance(), refNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settleToAvailable(Long shopId, BigDecimal amount, String refNo, String remark) {
        ShopBalance balance = getOrCreate(shopId);
        balance.setPendingAmount(balance.getPendingAmount().subtract(amount));
        balance.setAvailableBalance(balance.getAvailableBalance().add(amount));
        balance.setTotalIncome(balance.getTotalIncome().add(amount));
        shopBalanceMapper.updateById(balance);
        writeFlow(shopId, 2, amount, balance.getAvailableBalance(), refNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freezeForWithdraw(Long shopId, BigDecimal amount, String refNo, String remark) {
        ShopBalance balance = getOrCreate(shopId);
        balance.setAvailableBalance(balance.getAvailableBalance().subtract(amount));
        balance.setFrozenAmount(balance.getFrozenAmount().add(amount));
        shopBalanceMapper.updateById(balance);
        writeFlow(shopId, 3, amount.negate(), balance.getAvailableBalance(), refNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdrawSuccess(Long shopId, BigDecimal amount, String refNo, String remark) {
        ShopBalance balance = getOrCreate(shopId);
        balance.setFrozenAmount(balance.getFrozenAmount().subtract(amount));
        balance.setWithdrawnAmount(balance.getWithdrawnAmount().add(amount));
        shopBalanceMapper.updateById(balance);
        writeFlow(shopId, 4, amount.negate(), balance.getAvailableBalance(), refNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfreezeForReject(Long shopId, BigDecimal amount, String refNo, String remark) {
        ShopBalance balance = getOrCreate(shopId);
        balance.setFrozenAmount(balance.getFrozenAmount().subtract(amount));
        balance.setAvailableBalance(balance.getAvailableBalance().add(amount));
        shopBalanceMapper.updateById(balance);
        writeFlow(shopId, 5, amount, balance.getAvailableBalance(), refNo, remark);
    }

    private void writeFlow(Long shopId, Integer type, BigDecimal amount, BigDecimal balanceAfter,
                           String refNo, String remark) {
        BalanceFlow flow = new BalanceFlow();
        flow.setFlowNo(NoGenerator.flowNo());
        flow.setShopId(shopId);
        flow.setType(type);
        flow.setAmount(amount);
        flow.setBalanceAfter(balanceAfter);
        flow.setRefNo(refNo);
        flow.setRemark(remark);
        balanceFlowMapper.insert(flow);
    }
}
