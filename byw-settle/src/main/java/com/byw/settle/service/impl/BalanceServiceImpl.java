package com.byw.settle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.settle.dto.BalanceFlowDTO;
import com.byw.api.settle.dto.ShopBalanceDTO;
import com.byw.common.core.exception.BusinessException;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 商家余额账户变动实现。
 *
 * <p>资金变动全部走 {@link ShopBalanceMapper} 的原子 UPDATE（带余额下限守卫），
 * 不再"查出实体 → 内存加减 → updateById"，避免并发丢更新与超额提现；
 * 余额与流水在同一事务内写入，任一步失败整体回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceServiceImpl implements BalanceService {

    private final ShopBalanceMapper shopBalanceMapper;
    private final BalanceFlowMapper balanceFlowMapper;

    private static final String[] FLOW_TYPE_DESC = {
            "", "结算待入账", "结算入账", "提现冻结", "提现成功", "提现驳回解冻", "退款冲减"
    };

    /** 退款冲减流水类型 */
    private static final int FLOW_TYPE_REFUND_DEDUCT = 6;

    @Override
    public ShopBalance getOrCreate(Long shopId) {
        if (shopId == null) {
            throw new BusinessException("店铺ID不能为空");
        }
        ShopBalance balance = selectByShopId(shopId);
        if (balance != null) {
            return balance;
        }
        ShopBalance created = new ShopBalance();
        created.setShopId(shopId);
        created.setTotalIncome(BigDecimal.ZERO);
        created.setPendingAmount(BigDecimal.ZERO);
        created.setAvailableBalance(BigDecimal.ZERO);
        created.setFrozenAmount(BigDecimal.ZERO);
        created.setWithdrawnAmount(BigDecimal.ZERO);
        try {
            shopBalanceMapper.insert(created);
            return created;
        } catch (DuplicateKeyException e) {
            // 并发首次访问：唯一索引 uk_shop_id 兜底，回读已建账户
            ShopBalance existing = selectByShopId(shopId);
            if (existing == null) {
                throw new BusinessException("店铺余额账户初始化失败");
            }
            return existing;
        }
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
        assertPositive(amount);
        getOrCreate(shopId);
        requireApplied(shopBalanceMapper.addPending(shopId, amount), "结算待入账失败：余额账户不存在");
        writeFlow(shopId, 1, amount, currentAvailable(shopId), refNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settleToAvailable(Long shopId, BigDecimal amount, String refNo, String remark) {
        assertPositive(amount);
        getOrCreate(shopId);
        requireApplied(shopBalanceMapper.settleToAvailable(shopId, amount),
                "结算入账失败：待结算金额不足或已入账");
        writeFlow(shopId, 2, amount, currentAvailable(shopId), refNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freezeForWithdraw(Long shopId, BigDecimal amount, String refNo, String remark) {
        assertPositive(amount);
        getOrCreate(shopId);
        requireApplied(shopBalanceMapper.freezeForWithdraw(shopId, amount),
                "提现冻结失败：可提现余额不足");
        writeFlow(shopId, 3, amount.negate(), currentAvailable(shopId), refNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdrawSuccess(Long shopId, BigDecimal amount, String refNo, String remark) {
        assertPositive(amount);
        getOrCreate(shopId);
        requireApplied(shopBalanceMapper.withdrawSuccess(shopId, amount),
                "提现打款失败：冻结金额不足或已处理");
        writeFlow(shopId, 4, amount.negate(), currentAvailable(shopId), refNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfreezeForReject(Long shopId, BigDecimal amount, String refNo, String remark) {
        assertPositive(amount);
        getOrCreate(shopId);
        requireApplied(shopBalanceMapper.unfreezeForReject(shopId, amount),
                "提现解冻失败：冻结金额不足或已处理");
        writeFlow(shopId, 5, amount, currentAvailable(shopId), refNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deductPendingForRefund(Long shopId, BigDecimal amount, String refNo, String remark) {
        assertPositive(amount);
        getOrCreate(shopId);
        requireApplied(shopBalanceMapper.reducePending(shopId, amount),
                "退款冲减待结算失败：待结算金额不足");
        writeFlow(shopId, FLOW_TYPE_REFUND_DEDUCT, amount.negate(), currentAvailable(shopId), refNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deductAvailableForRefund(Long shopId, BigDecimal amount, String refNo, String remark) {
        assertPositive(amount);
        getOrCreate(shopId);
        // 扣不动说明商家已把钱提现走了，显式失败让调用方告警人工追偿，不把余额扣成负数
        requireApplied(shopBalanceMapper.reduceAvailable(shopId, amount),
                "退款冲减可用余额失败：余额不足（可能已提现），需人工追偿");
        writeFlow(shopId, FLOW_TYPE_REFUND_DEDUCT, amount.negate(), currentAvailable(shopId), refNo, remark);
    }

    @Override
    public boolean hasRefundDeduction(String refNo) {
        if (refNo == null || refNo.isEmpty()) {
            return false;
        }
        Long count = balanceFlowMapper.selectCount(new LambdaQueryWrapper<BalanceFlow>()
                .eq(BalanceFlow::getType, FLOW_TYPE_REFUND_DEDUCT)
                .eq(BalanceFlow::getRefNo, refNo));
        return count != null && count > 0;
    }

    /**
     * 原子更新影响行数为 0：说明 WHERE 里的余额守卫不满足（余额不足/账户不存在），
     * 必须显式失败并回滚，绝不能静默继续写流水。
     */
    private void requireApplied(int rows, String message) {
        if (rows == 0) {
            throw new BusinessException(message);
        }
    }

    private void assertPositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("金额必须大于 0");
        }
    }

    private ShopBalance selectByShopId(Long shopId) {
        return shopBalanceMapper.selectOne(new LambdaQueryWrapper<ShopBalance>()
                .eq(ShopBalance::getShopId, shopId));
    }

    /** 读取变动后的可用余额，作为流水快照；同一事务内刚更新的行可见 */
    private BigDecimal currentAvailable(Long shopId) {
        ShopBalance latest = selectByShopId(shopId);
        if (latest == null || latest.getAvailableBalance() == null) {
            return BigDecimal.ZERO;
        }
        return latest.getAvailableBalance();
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
