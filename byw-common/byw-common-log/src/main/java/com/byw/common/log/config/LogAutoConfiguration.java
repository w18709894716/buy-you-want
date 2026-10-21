package com.byw.common.log.config;

import com.byw.common.log.aspect.OperationLogAspect;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(ProceedingJoinPoint.class)
public class LogAutoConfiguration {

    /**
     * 切面需要 RocketMQTemplate 把审计日志投递到 byw-user 落库；
     * 用 ObjectProvider 注入，因此没有 RocketMQ 的服务依旧可以只用文件日志。
     */
    @Bean
    public OperationLogAspect operationLogAspect(ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider) {
        return new OperationLogAspect(rocketMQTemplateProvider);
    }
}
