package com.byw.common.log.aspect;

import com.byw.common.core.trace.TraceIdHolder;
import com.byw.common.log.annotation.OperationLog;
import com.byw.common.log.dto.OperationLogMessage;
import com.byw.common.rocketmq.constant.RocketMQTopics;
import com.byw.common.security.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 操作审计切面：既写文件日志（原有行为），也把结构化日志经 RocketMQ 发给 byw-user 落库，
 * 供管理端"操作日志"页面查询。
 *
 * <p>三条硬约束：
 * <ol>
 *   <li><b>绝不影响业务</b>：构建/投递消息的任何异常都只 warn；</li>
 *   <li><b>不记录假成功</b>：成功且处于事务中时，等事务提交后再投递 ——
 *       否则消费者可能先看到日志、随后业务回滚（审计说成功、库里却没有）；</li>
 *   <li><b>失败要留痕</b>：抛异常的调用立即投递（该事务不会提交），失败尝试同样被审计。</li>
 * </ol>
 *
 * <p>字段长度与 {@code t_operation_log} 列宽对齐（超长截断），避免消费者因单条日志超长反复失败。
 */
@Slf4j
@Aspect
public class OperationLogAspect {

    private static final int MAX_DESCRIPTION = 200;
    private static final int MAX_METHOD = 200;
    private static final int MAX_URI = 300;
    private static final int MAX_IP = 64;
    private static final int MAX_ERROR = 500;

    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;

    public OperationLogAspect(ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider) {
        this.rocketMQTemplateProvider = rocketMQTemplateProvider;
    }

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = signature.getMethod().getName();
        Long userId = UserContext.getUserId();

        log.info("[操作日志] 模块={}, 操作={}, 描述={}, 用户={}, 方法={}.{}",
                operationLog.module(), operationLog.operation(), operationLog.description(),
                userId, className, methodName);

        boolean success = true;
        String errorMsg = null;
        try {
            Object result = joinPoint.proceed();
            log.info("[操作日志] 执行成功, 耗时={}ms", System.currentTimeMillis() - startTime);
            return result;
        } catch (Throwable e) {
            success = false;
            errorMsg = e.getMessage();
            log.error("[操作日志] 执行失败, 耗时={}ms, 错误={}", System.currentTimeMillis() - startTime, errorMsg);
            throw e;
        } finally {
            try {
                publish(buildMessage(operationLog, className, methodName, success, errorMsg,
                        System.currentTimeMillis() - startTime));
            } catch (Exception e) {
                log.warn("操作日志记录失败（不影响业务）: {}", e.getMessage());
            }
        }
    }

    private OperationLogMessage buildMessage(OperationLog annotation, String className, String methodName,
                                            boolean success, String errorMsg, long costMs) {
        OperationLogMessage message = new OperationLogMessage();
        message.setModule(annotation.module());
        message.setOperation(annotation.operation());
        message.setDescription(truncate(annotation.description(), MAX_DESCRIPTION));
        message.setOperatorId(UserContext.getUserId());
        message.setOperatorName(UserContext.getUsername());
        String userType = UserContext.getUserType();
        message.setOperatorType(userType == null || userType.isBlank() ? "unknown" : userType);
        message.setMethod(truncate(className + "." + methodName, MAX_METHOD));
        message.setSuccess(success);
        message.setErrorMsg(truncate(errorMsg, MAX_ERROR));
        message.setCostMs(costMs);
        message.setTraceId(TraceIdHolder.get());

        HttpServletRequest request = currentRequest();
        if (request != null) {
            message.setRequestUri(truncate(request.getRequestURI(), MAX_URI));
            message.setRequestMethod(request.getMethod());
            message.setClientIp(truncate(resolveClientIp(request), MAX_IP));
        }
        return message;
    }

    private void publish(OperationLogMessage message) {
        RocketMQTemplate template = rocketMQTemplateProvider.getIfAvailable();
        if (template == null) {
            // 未接入 RocketMQ 的服务：只保留文件日志
            return;
        }
        if (!message.isSuccess()) {
            send(template, message);
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_COMMITTED) {
                        send(template, message);
                    }
                }
            });
            return;
        }
        send(template, message);
    }

    /**
     * 异步投递：审计日志绝不能占用业务请求的响应时间。
     *
     * <p>曾用 {@code syncSend}，broker 抖动时会阻塞到生产者超时（3s），直接把调用方（BFF）
     * 的 Feign 读超时（5s）打爆，业务接口变成 500 —— 审计拖垮业务，绝对不能接受。
     * 改为 asyncSend：立即返回，失败只 warn（文件日志仍在）。
     */
    private void send(RocketMQTemplate template, OperationLogMessage message) {
        try {
            template.asyncSend(RocketMQTopics.OPERATION_LOG, message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    // 正常投递，无需记录
                }

                @Override
                public void onException(Throwable e) {
                    log.warn("操作日志投递失败（不影响业务）: module={}, operation={}, err={}",
                            message.getModule(), message.getOperation(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("操作日志投递异常（不影响业务）: module={}, operation={}, err={}",
                    message.getModule(), message.getOperation(), e.getMessage());
        }
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servletAttributes ? servletAttributes.getRequest() : null;
    }

    /** 审计展示用；仅取 X-Forwarded-For 第一跳，不参与任何鉴权判定 */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
