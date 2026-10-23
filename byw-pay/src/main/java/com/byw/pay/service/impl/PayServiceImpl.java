package com.byw.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.byw.api.order.OrderFeignClient;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.api.pay.dto.PayOrderDTO;
import com.byw.common.core.result.R;
import com.byw.api.pay.dto.RefundInfoDTO;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.exception.ResultCode;
import com.byw.common.core.util.SnowflakeIdGenerator;
import com.byw.pay.entity.PayOrder;
import com.byw.pay.entity.RefundRecord;
import com.byw.pay.mapper.PayOrderMapper;
import com.byw.pay.mapper.RefundRecordMapper;
import com.byw.pay.producer.PayCallbackContext;
import com.byw.pay.producer.PayEventProducer;
import com.byw.pay.service.PayService;
import com.byw.common.security.context.UserContext;
import com.byw.pay.strategy.PayStrategy;
import com.byw.pay.strategy.PayStrategyFactory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayServiceImpl implements PayService {

    private final PayOrderMapper payOrderMapper;
    private final RefundRecordMapper refundRecordMapper;
    private final PayStrategyFactory payStrategyFactory;
    private final PayEventProducer payEventProducer;
    private final OrderFeignClient orderFeignClient;

    /**
     * 模拟支付回调开关（演示/本地联调）。
     * 开启时回调不做渠道验签，仅允许支付单归属用户触发；生产必须置 false 并接入真实渠道验签。
     *
     * <p>代码默认值取 {@code false}（fail-closed）：漏配时表现为"模拟支付不可用"，
     * 而不是"生产上任何人凭支付单号就能把订单标记为已支付"。本地/演示环境由
     * {@code docs/deploy/nacos/services/byw-pay.yml} 的 {@code ${BYW_PAY_MOCK_ENABLED:true}} 显式打开。
     */
    @Value("${byw.pay.mock-enabled:false}")
    private boolean mockEnabled;

    @PostConstruct
    public void warnIfMockEnabled() {
        if (mockEnabled) {
            log.warn("byw.pay.mock-enabled=true：支付回调为模拟通道，仅支付单归属用户可触发；"
                    + "生产环境请置为 false 并接入真实渠道验签");
        }
    }

    @Override
    public PayOrderDTO createPayOrder(String orderNo, Long userId, BigDecimal amount, String channel) {
        // 金额以订单为准：客户端传入的 amount 只用于核对，绝不允许"自报金额"支付。
        // 本方法不再包事务：内部只有一次 insert，且要在事务外调用订单服务，避免长时间占用连接。
        R<OrderDetailDTO> orderResult = orderFeignClient.getOrderDetail(orderNo);
        if (orderResult == null || !orderResult.isSuccess() || orderResult.getData() == null) {
            throw new BusinessException("订单不存在");
        }
        OrderDetailDTO order = orderResult.getData();
        if (userId == null || !userId.equals(order.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (order.getStatus() == null || order.getStatus() != 0) {
            throw new BusinessException("订单当前状态不可支付");
        }
        BigDecimal payAmount = order.getPayAmount() == null ? BigDecimal.ZERO : order.getPayAmount();
        if (amount != null && amount.compareTo(payAmount) != 0) {
            log.warn("支付金额与订单不一致，拒绝创建支付单: orderNo={}, clientAmount={}, orderAmount={}",
                    orderNo, amount, payAmount);
            throw new BusinessException("支付金额与订单不一致，请刷新后重试");
        }
        if (payAmount.signum() <= 0) {
            throw new BusinessException("订单应付金额异常，无法发起支付");
        }

        // 同一订单已有待支付支付单时复用，避免连点/重试产生多张支付单
        PayOrder pending = payOrderMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getOrderNo, orderNo)
                .eq(PayOrder::getStatus, 0)
                .orderByDesc(PayOrder::getId)
                .last("LIMIT 1"));
        if (pending != null) {
            Map<String, String> existingPayResult =
                    payStrategyFactory.getStrategy(channel).pay(pending.getPayNo(), pending.getAmount());
            PayOrderDTO existingDto = new PayOrderDTO();
            BeanUtils.copyProperties(pending, existingDto);
            existingDto.setPayUrl(existingPayResult.get("payUrl"));
            log.info("复用待支付支付单: payNo={}, orderNo={}", pending.getPayNo(), orderNo);
            return existingDto;
        }

        // 生成支付单号
        String payNo = generatePayNo();

        // 使用策略模式发起支付
        PayStrategy strategy = payStrategyFactory.getStrategy(channel);
        Map<String, String> payResult = strategy.pay(payNo, payAmount);

        // 创建支付单
        PayOrder payOrder = new PayOrder();
        payOrder.setPayNo(payNo);
        payOrder.setOrderNo(orderNo);
        payOrder.setUserId(userId);
        payOrder.setAmount(payAmount);
        payOrder.setPayChannel(channel);
        payOrder.setStatus(0); // 待支付
        payOrderMapper.insert(payOrder);

        // 构建返回DTO
        PayOrderDTO dto = new PayOrderDTO();
        BeanUtils.copyProperties(payOrder, dto);
        dto.setPayUrl(payResult.get("payUrl"));

        log.info("创建支付单成功: payNo={}, orderNo={}, channel={}, amount={}", payNo, orderNo, channel, payAmount);
        return dto;
    }

    @Override
    public Integer getPayStatus(String payNo) {
        PayOrder payOrder = getPayOrderByNo(payNo);
        return payOrder.getStatus();
    }

    @Override
    public Long getPayOrderUserId(String payNo) {
        return getPayOrderByNo(payNo).getUserId();
    }

    /**
     * 真实渠道回调：验签由控制层（{@code PayCallbackSignature}）在进入本方法前完成，
     * 因此这里不再重复做"归属用户"校验 —— 渠道回调没有登录态。
     */
    @Override
    public void handlePayCallback(String channel, String payNo, String tradeNo, String callbackContent) {
        applyCallbackResult(channel, payNo, tradeNo, callbackContent);
    }

    /**
     * 模拟回调：没有渠道验签，退化为两道门槛 —— 开关必须显式打开，且调用方必须是支付单归属用户。
     * 任何一条不满足都拒绝，避免"猜到 payNo 就能把别人订单改成已支付"。
     */
    @Override
    public void handleMockCallback(String channel, String payNo, String tradeNo) {
        requireMockEnabled(payNo);
        requirePayOrderOwner(payNo, UserContext.getUserId());
        applyCallbackResult(channel, payNo, tradeNo, null);
    }

    /**
     * 模拟支付（服务间）：与 {@link #handleMockCallback} 共用同一道闸门，走同一条落库 + 事务消息路径。
     *
     * <p>userId 显式入参而不读 {@code UserContext}：调用方是 byw-order 的 Feign，
     * 依赖上下文透传等于把资金动作建立在"身份头一定在"的假设上。
     */
    @Override
    public void mockPay(String payNo, Long userId) {
        requireMockEnabled(payNo);
        PayOrder payOrder = requirePayOrderOwner(payNo, userId);
        // 渠道以支付单自身为准，不由调用方指定，避免拿另一个渠道的策略处理这张单
        applyCallbackResult(payOrder.getPayChannel(), payNo, "MOCK" + payNo, null);
    }

    /** 模拟通道开关：未显式打开一律拒绝（生产置 false 后所有模拟支付路径彻底关闭） */
    private void requireMockEnabled(String payNo) {
        if (!mockEnabled) {
            log.warn("模拟支付回调未开启，拒绝: payNo={}", payNo);
            throw new BusinessException(ResultCode.FORBIDDEN, "模拟支付回调未开启");
        }
    }

    /** 模拟通道无渠道验签，归属校验是唯一门槛：非支付单归属用户一律拒绝 */
    private PayOrder requirePayOrderOwner(String payNo, Long userId) {
        PayOrder payOrder = getPayOrderByNo(payNo);
        if (userId == null || !userId.equals(payOrder.getUserId())) {
            log.warn("模拟支付回调被拒绝：调用方非支付单归属用户, payNo={}, caller={}", payNo, userId);
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return payOrder;
    }

    /** 回调结果的公共处理：状态幂等判断 + 支付状态查询 + 事务消息/落库 */
    private void applyCallbackResult(String channel, String payNo, String tradeNo, String callbackContent) {
        PayOrder payOrder = getPayOrderByNo(payNo);

        if (payOrder.getStatus() != 0) {
            log.warn("支付单状态异常，忽略回调: payNo={}, status={}", payNo, payOrder.getStatus());
            return;
        }

        // 使用策略验证支付状态
        PayStrategy strategy = payStrategyFactory.getStrategy(channel);
        Integer payStatus = strategy.queryStatus(payNo);

        if (payStatus == 1) {
            // 支付成功：发事务消息，把"支付单置为已支付"交给本地事务在半消息落盘后执行，
            // 使落库与消息投递同生共死。
            // 注意本方法不能加 @Transactional：否则本地事务会并入外层事务，
            // broker 提交半消息时外层事务尚未提交，dual-write 窗口依然存在。
            boolean committed = payEventProducer.sendPaymentResultInTransaction(payOrder.getOrderNo(), payNo,
                    new PayCallbackContext(payNo, tradeNo, callbackContent));
            if (!committed) {
                throw new BusinessException(ResultCode.PAY_FAILED, "支付结果处理失败，请重试");
            }
            log.info("支付成功，支付结果事务消息已提交: payNo={}, orderNo={}", payNo, payOrder.getOrderNo());
        } else {
            // 非成功结果（支付失败/处理中）没有消息要发，直接落库
            payOrder.setStatus(payStatus);
            payOrder.setChannelTradeNo(tradeNo);
            payOrder.setCallbackContent(callbackContent);
            payOrderMapper.updateById(payOrder);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completePayment(String payNo, String tradeNo, String callbackContent) {
        PayOrder payOrder = getPayOrderByNo(payNo);
        if (payOrder.getStatus() != 0) {
            // 同一支付单只会有一条半消息，此处仅防御异常调用，避免覆盖 payTime
            log.warn("支付单已处理，跳过本地事务: payNo={}, status={}", payNo, payOrder.getStatus());
            return;
        }
        payOrder.setStatus(1);
        payOrder.setChannelTradeNo(tradeNo);
        payOrder.setCallbackContent(callbackContent);
        payOrder.setPayTime(LocalDateTime.now());
        payOrderMapper.updateById(payOrder);
        log.info("支付单本地事务提交成功: payNo={}, orderNo={}", payNo, payOrder.getOrderNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refund(String orderNo, BigDecimal amount, String reason, String refNo) {
        // 幂等闸门：同一业务键（售后单号）重复驱动退款直接返回成功，避免重复退款。
        // 唯一索引 uk_ref_no 是并发场景的最终兜底（见下方 DuplicateKeyException 分支）。
        if (refNo != null && !refNo.isEmpty()) {
            RefundRecord existing = refundRecordMapper.selectOne(new LambdaQueryWrapper<RefundRecord>()
                    .eq(RefundRecord::getRefNo, refNo)
                    .last("LIMIT 1"));
            if (existing != null) {
                log.info("退款已存在，幂等返回: refNo={}, refundNo={}", refNo, existing.getRefundNo());
                return;
            }
        }

        // 查找可退款支付单：先按本单查，未命中再回溯父订单（聚合支付的支付单挂在父订单号上）
        PayOrder payOrder = findRefundablePayOrder(orderNo);

        if (payOrder == null) {
            log.warn("退款失败，未找到已支付的支付单: orderNo={}", orderNo);
            throw new BusinessException("未找到已支付的订单");
        }
        // 金额边界：不允许退零/负数，也不允许单笔超过原支付金额
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("退款金额必须大于 0");
        }
        if (amount.compareTo(payOrder.getAmount()) > 0) {
            throw new BusinessException("退款金额不能超过支付金额");
        }
        // 累计退款不得超过原支付金额：同一次聚合支付下多个子订单先后退款时，
        // 单笔都在上限内但总和可能超出。并发下存在读-改-写窗口，
        // 由 t_refund_record 的唯一索引与售后幂等键共同兜住主要路径。
        BigDecimal refundedTotal = refundRecordMapper.selectList(new LambdaQueryWrapper<RefundRecord>()
                        .eq(RefundRecord::getPayNo, payOrder.getPayNo()))
                .stream()
                .map(RefundRecord::getRefundAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (refundedTotal.add(amount).compareTo(payOrder.getAmount()) > 0) {
            throw new BusinessException("累计退款金额不能超过支付金额");
        }

        // 创建退款记录
        String refundNo = generateRefundNo();
        RefundRecord refundRecord = new RefundRecord();
        refundRecord.setRefundNo(refundNo);
        refundRecord.setPayNo(payOrder.getPayNo());
        refundRecord.setOrderNo(orderNo);
        refundRecord.setUserId(payOrder.getUserId());
        refundRecord.setRefundAmount(amount);
        refundRecord.setReason(reason);
        refundRecord.setRefNo(refNo);
        refundRecord.setStatus(1); // 模拟退款成功
        try {
            refundRecordMapper.insert(refundRecord);
        } catch (DuplicateKeyException e) {
            // 并发重复驱动被唯一索引拦下：首笔已退款，此处按幂等成功处理
            log.warn("并发退款被唯一索引拦下，幂等返回: refNo={}", refNo);
            return;
        }

        // 更新支付单状态为已退款
        payOrder.setStatus(3);
        payOrderMapper.updateById(payOrder);

        // 订单状态流转由 byw-order 售后流程统一编排（商品级退款不能整单关闭），此处不再回写
        log.info("退款成功: refundNo={}, orderNo={}, amount={}", refundNo, orderNo, amount);
    }

    @Override
    public RefundInfoDTO getRefundByOrderNo(String orderNo) {
        RefundRecord record = refundRecordMapper.selectOne(
                new LambdaQueryWrapper<RefundRecord>()
                        .eq(RefundRecord::getOrderNo, orderNo)
                        .orderByDesc(RefundRecord::getCreatedAt)
                        .last("LIMIT 1"));
        if (record == null) {
            return null;
        }
        RefundInfoDTO dto = new RefundInfoDTO();
        dto.setRefundNo(record.getRefundNo());
        dto.setOrderNo(record.getOrderNo());
        dto.setRefundAmount(record.getRefundAmount());
        dto.setReason(record.getReason());
        dto.setStatus(record.getStatus());
        dto.setCreatedAt(record.getCreatedAt());
        dto.setUpdatedAt(record.getUpdatedAt());
        // 关联支付单获取原支付渠道（原路退回目的地）
        PayOrder payOrder = payOrderMapper.selectOne(
                new LambdaQueryWrapper<PayOrder>()
                        .eq(PayOrder::getPayNo, record.getPayNo())
                        .last("LIMIT 1"));
        if (payOrder != null) {
            dto.setPayChannel(payOrder.getPayChannel());
        }
        return dto;
    }

    // ==================== 私有方法 ====================

    private PayOrder getPayOrderByNo(String payNo) {
        PayOrder payOrder = payOrderMapper.selectOne(
                new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getPayNo, payNo));
        if (payOrder == null) {
            throw new BusinessException("支付单不存在");
        }
        return payOrder;
    }

    /**
     * 查找订单对应的可退款支付单。
     * 聚合支付场景下支付单记录的是父订单号，子订单退款时需回溯父订单号查找；
     * 状态含已退款(3)：同一父单下多个子订单先后退款时，支付单已被首笔退款置 3，后续仍可命中。
     */
    private PayOrder findRefundablePayOrder(String orderNo) {
        PayOrder payOrder = selectPaidOrder(orderNo);
        if (payOrder != null) {
            return payOrder;
        }
        // 回溯父订单号（子订单本身无支付单）
        try {
            R<OrderDetailDTO> orderResult = orderFeignClient.getOrderDetail(orderNo);
            if (orderResult != null && orderResult.isSuccess() && orderResult.getData() != null) {
                String parentOrderNo = orderResult.getData().getParentOrderNo();
                if (parentOrderNo != null && !parentOrderNo.isEmpty()) {
                    return selectPaidOrder(parentOrderNo);
                }
            }
        } catch (Exception e) {
            log.warn("查询订单父单号失败: orderNo={}, error={}", orderNo, e.getMessage());
        }
        return null;
    }

    /** 按订单号查支付成功(1)/已退款(3)的支付单，取最新一条 */
    private PayOrder selectPaidOrder(String orderNo) {
        return payOrderMapper.selectOne(
                new LambdaQueryWrapper<PayOrder>()
                        .eq(PayOrder::getOrderNo, orderNo)
                        .in(PayOrder::getStatus, 1, 3)
                        .orderByDesc(PayOrder::getCreatedAt)
                        .last("LIMIT 1"));
    }

    /** 支付单号：PAY + 雪花ID（全局唯一，多实例安全） */
    private String generatePayNo() {
        return "PAY" + SnowflakeIdGenerator.nextIdStr();
    }

    /** 退款单号：REF + 雪花ID */
    private String generateRefundNo() {
        return "REF" + SnowflakeIdGenerator.nextIdStr();
    }
}
