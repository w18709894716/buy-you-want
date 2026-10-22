package com.byw.settle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byw.settle.entity.ShopBalance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 商家余额账户 Mapper。
 *
 * <p>资金变动一律走下面的原子 UPDATE：由数据库在一条语句内完成"读-改-写"，
 * 并在 WHERE 中带上余额下限守卫（如 {@code available_balance >= amount}）。
 * 原先"select 出来 add 再 updateById"在并发（T+N 入账 vs 提现冻结）下会丢更新，
 * 也可能把可用余额扣成负数。
 */
@Mapper
public interface ShopBalanceMapper extends BaseMapper<ShopBalance> {

    /** 结算待入账：pending += amount */
    @Update("UPDATE t_shop_balance SET pending_amount = pending_amount + #{amount}, "
            + "updated_at = NOW() WHERE shop_id = #{shopId} AND deleted = 0")
    int addPending(@Param("shopId") Long shopId, @Param("amount") BigDecimal amount);

    /** 结算入账：pending -= amount、available += amount、total_income += amount（要求待结算额充足） */
    @Update("UPDATE t_shop_balance SET pending_amount = pending_amount - #{amount}, "
            + "available_balance = available_balance + #{amount}, "
            + "total_income = total_income + #{amount}, updated_at = NOW() "
            + "WHERE shop_id = #{shopId} AND deleted = 0 AND pending_amount >= #{amount}")
    int settleToAvailable(@Param("shopId") Long shopId, @Param("amount") BigDecimal amount);

    /** 提现冻结：available -= amount、frozen += amount（要求可用余额充足，防超额提现） */
    @Update("UPDATE t_shop_balance SET available_balance = available_balance - #{amount}, "
            + "frozen_amount = frozen_amount + #{amount}, updated_at = NOW() "
            + "WHERE shop_id = #{shopId} AND deleted = 0 AND available_balance >= #{amount}")
    int freezeForWithdraw(@Param("shopId") Long shopId, @Param("amount") BigDecimal amount);

    /** 提现打款：frozen -= amount、withdrawn += amount（要求冻结额充足） */
    @Update("UPDATE t_shop_balance SET frozen_amount = frozen_amount - #{amount}, "
            + "withdrawn_amount = withdrawn_amount + #{amount}, updated_at = NOW() "
            + "WHERE shop_id = #{shopId} AND deleted = 0 AND frozen_amount >= #{amount}")
    int withdrawSuccess(@Param("shopId") Long shopId, @Param("amount") BigDecimal amount);

    /** 提现驳回解冻：frozen -= amount、available += amount（要求冻结额充足） */
    @Update("UPDATE t_shop_balance SET frozen_amount = frozen_amount - #{amount}, "
            + "available_balance = available_balance + #{amount}, updated_at = NOW() "
            + "WHERE shop_id = #{shopId} AND deleted = 0 AND frozen_amount >= #{amount}")
    int unfreezeForReject(@Param("shopId") Long shopId, @Param("amount") BigDecimal amount);

    /** 退款冲减待结算：pending -= amount（结算单仍在冷静期，钱还没进商家可用余额） */
    @Update("UPDATE t_shop_balance SET pending_amount = pending_amount - #{amount}, updated_at = NOW() "
            + "WHERE shop_id = #{shopId} AND deleted = 0 AND pending_amount >= #{amount}")
    int reducePending(@Param("shopId") Long shopId, @Param("amount") BigDecimal amount);

    /**
     * 退款冲减可用余额：available -= amount、total_income -= amount（结算单已入账后的退款）。
     *
     * <p>守卫 {@code available_balance >= amount}：商家可能已把这笔钱提现走了，此时扣不动，
     * 由调用方按失败处理并告警人工追偿，绝不把余额扣成负数。
     */
    @Update("UPDATE t_shop_balance SET available_balance = available_balance - #{amount}, "
            + "total_income = total_income - #{amount}, updated_at = NOW() "
            + "WHERE shop_id = #{shopId} AND deleted = 0 AND available_balance >= #{amount}")
    int reduceAvailable(@Param("shopId") Long shopId, @Param("amount") BigDecimal amount);
}
