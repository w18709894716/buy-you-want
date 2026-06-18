package com.byw.common.log.config;

import com.byw.common.log.aspect.OperationLogAspect;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(ProceedingJoinPoint.class)
public class LogAutoConfiguration {

    @Bean
    public OperationLogAspect operationLogAspect() {
        return new OperationLogAspect();
    }
}
