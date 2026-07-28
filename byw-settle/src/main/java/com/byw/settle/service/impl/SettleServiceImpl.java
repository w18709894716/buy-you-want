package com.byw.settle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.order.OrderFeignClient;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.api.settle.dto.SettleRecordDTO;
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
            log.warn("获取订单详情失败，无法结算: orderNo={}", orderNo);
            return;
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
