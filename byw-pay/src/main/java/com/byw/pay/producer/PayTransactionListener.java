package com.byw.pay.producer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.byw.pay.entity.PayOrder;
import com.byw.pay.mapper.PayOrderMapper;
import com.byw.pay.service.PayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 支付结果事务消息的本地事务。
 *
 * <p>半消息落盘后执行"支付单置为已支付"，成功即提交投递、失败即回滚半消息；
 * 本地事务提交后进程崩溃、endTransaction 没发出去时，由 broker 回查 {@link #checkLocalTransaction}
 * 依据落库状态补提交，因此不再存在"支付单已改但消息没发"或"消息发了但支付单回滚"的窗口。
 */
@Slf4j
@RocketMQTransactionListener
@RequiredArgsConstructor
public class PayTransactionListener implements RocketMQLocalTransactionListener {

    private final PayService payService;
    private final PayOrderMapper payOrderMapper;
    private final ObjectMapper objectMapper;

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        PayCallbackContext context = (PayCallbackContext) arg;
        try {
            payService.completePayment(context.payNo(), context.tradeNo(), context.callbackContent());
            return RocketMQLocalTransactionState.COMMIT;
        } catch (Exception e) {
            log.error("支付单本地事务执行失败，回滚支付结果消息: payNo={}", context.payNo(), e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        String payNo = readPayNo(msg);
        if (payNo == null) {
            log.error("支付结果消息回查失败：无法定位支付单，放弃投递");
            return RocketMQLocalTransactionState.ROLLBACK;
        }

        PayOrder payOrder = payOrderMapper.selectOne(
                new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getPayNo, payNo));
        // 查不到或仍待支付(0) 说明本地事务从未提交，回滚半消息；
        // 其余状态说明本地事务提交过（之后可能又退款），必须投递
        if (payOrder == null || payOrder.getStatus() == null || payOrder.getStatus() == 0) {
            log.warn("支付结果消息回查：本地事务未提交，回滚: payNo={}", payNo);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        log.info("支付结果消息回查：本地事务已提交，投递: payNo={}, status={}", payNo, payOrder.getStatus());
        return RocketMQLocalTransactionState.COMMIT;
    }

    private String readPayNo(Message msg) {
        Object payload = msg.getPayload();
        if (!(payload instanceof byte[] body)) {
            log.error("支付结果消息体类型异常: {}", payload == null ? null : payload.getClass());
            return null;
        }
        try {
            Map<?, ?> event = objectMapper.readValue(body, Map.class);
            Object payNo = event.get("payNo");
            return payNo == null ? null : payNo.toString();
        } catch (IOException e) {
            log.error("支付结果消息体解析失败: {}", new String(body, StandardCharsets.UTF_8), e);
            return null;
        }
    }
}
