package com.byw.settle.service;

import com.byw.api.settle.dto.BalanceFlowDTO;
import com.byw.api.settle.dto.ShopBalanceDTO;
import com.byw.common.core.result.PageResult;
import com.byw.settle.entity.ShopBalance;

import java.math.BigDecimal;

/**
 * 商家余额账户变动服务：所有资金变动均在此写入余额账户 + 余额流水（同一事务）。
 */
public interface BalanceService {

    /** 获取（不存在则初始化）店铺余额账户 */
    ShopBalance getOrCreate(Long shopId);

    /** 查询店铺余额（DTO） */
    ShopBalanceDTO getBalanceDTO(Long shopId);

    /** 余额流水分页查询 */
    PageResult<BalanceFlowDTO> listFlows(Long shopId, Integer pageNum, Integer pageSize);

    /** 结算待入账：pending += amount（冷静期冻结中，flow type=1） */
    void addPending(Long shopId, BigDecimal amount, String refNo, String remark);

    /** 结算入账：pending -= amount，available += amount，total_income += amount（flow type=2） */
    void settleToAvailable(Long shopId, BigDecimal amount, String refNo, String remark);

    /** 提现冻结：available -= amount，frozen += amount（flow type=3） */
    void freezeForWithdraw(Long shopId, BigDecimal amount, String refNo, String remark);

    /** 提现成功：frozen -= amount，withdrawn += amount（flow type=4） */
    void withdrawSuccess(Long shopId, BigDecimal amount, String refNo, String remark);

    /** 提现驳回解冻：frozen -= amount，available += amount（flow type=5） */
    void unfreezeForReject(Long shopId, BigDecimal amount, String refNo, String remark);
}
