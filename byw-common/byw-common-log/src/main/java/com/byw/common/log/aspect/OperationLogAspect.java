package com.byw.common.log.aspect;

import com.byw.common.log.annotation.OperationLog;
import com.byw.common.security.context.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

@Slf4j
@Aspect
public class OperationLogAspect {

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

        Object result;
        try {
            result = joinPoint.proceed();
            long cost = System.currentTimeMillis() - startTime;
            log.info("[操作日志] 执行成功, 耗时={}ms", cost);
        } catch (Throwable e) {
            long cost = System.currentTimeMillis() - startTime;
            log.error("[操作日志] 执行失败, 耗时={}ms, 错误={}", cost, e.getMessage());
            throw e;
        }

        return result;
    }
}
