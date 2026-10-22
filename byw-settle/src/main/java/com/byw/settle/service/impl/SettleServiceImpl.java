package com.byw.settle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.order.OrderFeignClient;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.api.settle.dto.SettleRecordDTO;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.settle.entity.SettleRecord;
import com.byw.settle.mapper.SettleRecordMapper;
import com.byw.settle.service.BalanceService;
import com.byw.settle.service.CommissionService;
import com.byw.settle.service.SettleService;
import com.byw.settle.service.ShopNameResolver;
import com.byw.settle.util.NoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettleServiceImpl implements SettleService {

    private final SettleRecordMapper settleRecordMapper;
    private final OrderFeignClient orderFeignClient;
    private final CommissionService commissionService;
    private final BalanceService balanceService;
    private final ShopNameResolver shopNameResolver;
    private final SettleAccounting settleAccounting;

    /** 结算冷静期天数（收货后 T+N 天可用余额入账） */
    @Value("${byw.settle.cooling-days:7}")
    private int coolingDays;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settleOnReceive(String orderNo) {
        // 幂等：已存在结算单则跳过
        Long exists = settleRecordMapper.selectCount(new LambdaQueryWrapper<SettleRecord>()
                .eq(SettleRecord::getOrderNo, orderNo));
        if (exists != null && exists > 0) {
            log.info("结算单已存在，跳过: orderNo={}", orderNo);
            return;
        }

        R<OrderDetailDTO> resp = orderFeignClient.getOrderDetail(orderNo);
        if (resp == null || !resp.isSuccess() || resp.getData() == null) {
            // 抛出让 MQ 重试：这里多为订单服务抖动/网络超时，静默 return 会让结算单永久丢失
            throw new BusinessException("获取订单详情失败，无法结算: orderNo=" + orderNo
                    + (resp == null ? "" : ", resp=" + resp.getMessage()));
        }
        OrderDetailDTO order = resp.getData();

        // 仅结算子订单/普通订单（父订单仅聚合支付，不参与结算）
        if (order.getIsParent() != null && order.getIsParent() == 1) {
            log.info("父订单不参与结算，跳过: orderNo={}", orderNo);
            return;
        }
        if (order.getShopId() == null) {
            log.warn("订单缺少归属店铺，跳过结算: orderNo={}", orderNo);
            return;
        }

        BigDecimal orderAmount = order.getPayAmount() == null ? BigDecimal.ZERO : order.getPayAmount();
        BigDecimal commission = commissionService.calcCommission(order);
        if (commission.compareTo(orderAmount) > 0) {
            commission = orderAmount;
        }
        BigDecimal settleAmount = orderAmount.subtract(commission);

        LocalDateTime receiveTime = order.getReceiveTime() != null ? order.getReceiveTime() : LocalDateTime.now();
        LocalDateTime expectSettleTime = receiveTime.plusDays(coolingDays);

        SettleRecord record = new SettleRecord();
        record.setSettleNo(NoGenerator.settleNo());
        record.setOrderNo(order.getOrderNo());
        record.setParentOrderNo(order.getParentOrderNo());
        record.setShopId(order.getShopId());
        record.setUserId(order.getUserId());
        record.setOrderAmount(orderAmount);
        record.setCommissionAmount(commission);
        record.setSettleAmount(settleAmount);
        record.setStatus(0);
        record.setReceiveTime(receiveTime);
        record.setExpectSettleTime(expectSettleTime);
        record.setRemark("收货结算，冷静期" + coolingDays + "天");
        settleRecordMapper.insert(record);

        // 计入待结算(冷静期)
        balanceService.addPending(order.getShopId(), settleAmount, record.getSettleNo(), "订单结算待入账");
        log.info("生成结算单: settleNo={}, orderNo={}, shopId={}, orderAmount={}, commission={}, settleAmount={}, expectSettleTime={}",
                record.getSettleNo(), orderNo, order.getShopId(), orderAmount, commission, settleAmount, expectSettleTime);
    }

    @Override
    public int settleDueRecords() {
        List<SettleRecord> dueList = settleRecordMapper.selectList(new LambdaQueryWrapper<SettleRecord>()
                .eq(SettleRecord::getStatus, 0)
                .le(SettleRecord::getExpectSettleTime, LocalDateTime.now()));
        int count = 0;
        for (SettleRecord record : dueList) {
            try {
                if (settleAccounting.settleOne(record)) {
                    count++;
                }
            } catch (Exception e) {
                log.error("结算单入账失败: settleNo={}", record.getSettleNo(), e);
            }
        }
        if (count > 0) {
            log.info("T+N 结算入账完成，本次入账 {} 笔", count);
        }
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustOnRefund(String orderNo, BigDecimal refundAmount, String refNo) {
        if (refundAmount == null || refundAmount.signum() <= 0) {
            return;
        }
        // 无幂等键就无法保证不重复冲减，直接拒绝而不是冒险扣钱
        if (refNo == null || refNo.isEmpty()) {
            throw new BusinessException("退款冲减缺少业务幂等键");
        }
        SettleRecord record = settleRecordMapper.selectOne(new LambdaQueryWrapper<SettleRecord>()
                .eq(SettleRecord::getOrderNo, orderNo)
                .last("LIMIT 1"));
        if (record == null) {
            // 结算单在确认收货时才生成：收货前的退款（未发货仅退款、退货退款）本就无结算可冲减
            log.info("退款无对应结算单，跳过冲减: orderNo={}, refundAmount={}", orderNo, refundAmount);
            return;
        }
        if (record.getStatus() != null && record.getStatus() == 2) {
            log.info("结算单已因退款关闭，跳过: settleNo={}, orderNo={}", record.getSettleNo(), orderNo);
            return;
        }
        // 幂等：同一售后单号只冲减一次。冲减在 byw-order 的终态事务提交之后发起，
        // 进程崩溃重试或对账重驱都可能重复调用，靠余额流水的 refNo 判定。
        if (balanceService.hasRefundDeduction(refNo)) {
            log.info("退款冲减已处理过，跳过: settleNo={}, refNo={}", record.getSettleNo(), refNo);
            return;
        }

        BigDecimal orderAmount = record.getOrderAmount();
        BigDecimal settleAmount = record.getSettleAmount();
        if (orderAmount == null || orderAmount.signum() <= 0
                || settleAmount == null || settleAmount.signum() <= 0) {
            log.info("结算单无可冲减金额，跳过: settleNo={}, orderAmount={}, settleAmount={}",
                    record.getSettleNo(), orderAmount, settleAmount);
            return;
        }
        // 商家应担份额按结算占比折算：平台佣金那部分随退款一并让出，不由商家独自承担
        BigDecimal merchantShare = refundAmount.multiply(settleAmount)
                .divide(orderAmount, 2, RoundingMode.HALF_UP)
                .min(settleAmount);
        if (merchantShare.signum() <= 0) {
            log.info("退款折算后商家份额为 0，跳过: settleNo={}, refundAmount={}",
                    record.getSettleNo(), refundAmount);
            return;
        }

        if (record.getStatus() != null && record.getStatus() == 1) {
            // 已过冷静期入账：钱在商家可用余额里，从余额冲减（余额不足说明已提现，抛错交人工追偿）
            balanceService.deductAvailableForRefund(record.getShopId(), merchantShare, refNo,
                    "退款冲减已入账结算:" + orderNo);
            log.warn("结算入账后发生退款，已从可用余额冲减: settleNo={}, orderNo={}, merchantShare={}, refNo={}",
                    record.getSettleNo(), orderNo, merchantShare, refNo);
            return;
        }

        // 冷静期内：全额退完直接关单，部分退款下调应得金额；两者都要把待结算额度扣回来。
        // CAS 失败说明 T+N 入账任务已并发生效，抛错让调用方按"已入账"重驱，绝不静默漏扣。
        boolean fullRefund = merchantShare.compareTo(settleAmount) >= 0;
        int rows = fullRefund
                ? settleRecordMapper.closeForRefund(record.getId(), "退款全额冲减，结算关闭:" + refNo)
                : settleRecordMapper.reduceSettleAmount(record.getId(), merchantShare);
        if (rows == 0) {
            throw new BusinessException("结算单状态已变更，退款冲减失败: settleNo=" + record.getSettleNo());
        }
        balanceService.deductPendingForRefund(record.getShopId(),
                fullRefund ? settleAmount : merchantShare, refNo, "退款冲减待结算:" + orderNo);
        log.info("退款冲减待结算完成: settleNo={}, orderNo={}, merchantShare={}, fullRefund={}, refNo={}",
                record.getSettleNo(), orderNo, merchantShare, fullRefund, refNo);
    }

    @Override
    public PageResult<SettleRecordDTO> listSettleRecords(Long shopId, Integer status, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<SettleRecord> wrapper = new LambdaQueryWrapper<SettleRecord>()
                .eq(shopId != null, SettleRecord::getShopId, shopId)
                .eq(status != null, SettleRecord::getStatus, status)
                .orderByDesc(SettleRecord::getCreatedAt);
        IPage<SettleRecord> page = settleRecordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        List<Long> shopIds = page.getRecords().stream().map(SettleRecord::getShopId).collect(Collectors.toList());
        Map<Long, String> shopNames = shopNameResolver.resolve(shopIds);

        List<SettleRecordDTO> list = page.getRecords().stream().map(r -> {
            SettleRecordDTO dto = new SettleRecordDTO();
            BeanUtils.copyProperties(r, dto);
            dto.setShopName(shopNames.get(r.getShopId()));
            return dto;
        }).collect(Collectors.toList());
        return PageResult.of(list, page.getTotal(), pageNum, pageSize);
    }
}
