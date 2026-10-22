package com.byw.settle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byw.settle.entity.SettleRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface SettleRecordMapper extends BaseMapper<SettleRecord> {

    /**
     * 冷静期内部分退款：settle_amount -= amount。
     *
     * <p>WHERE 带 {@code status = 0} 与 {@code settle_amount >= amount}：前者是与 T+N 入账任务
     * （CAS 0→1）互斥的闸门，两者只有一个能生效；后者防止把应得金额扣成负数。
     */
    @Update("UPDATE t_settle_record SET settle_amount = settle_amount - #{amount}, updated_at = NOW() "
            + "WHERE id = #{id} AND status = 0 AND settle_amount >= #{amount} AND deleted = 0")
    int reduceSettleAmount(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /** 全额退款：结算单 待结算(0) → 已关闭(2)，settle_amount 保留原值供审计 */
    @Update("UPDATE t_settle_record SET status = 2, remark = #{remark}, updated_at = NOW() "
            + "WHERE id = #{id} AND status = 0 AND deleted = 0")
    int closeForRefund(@Param("id") Long id, @Param("remark") String remark);
}
