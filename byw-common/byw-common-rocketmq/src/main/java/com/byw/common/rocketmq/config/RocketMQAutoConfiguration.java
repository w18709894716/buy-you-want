package com.byw.common.rocketmq.config;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

@AutoConfiguration
@ConditionalOnClass(RocketMQTemplate.class)
public class RocketMQAutoConfiguration {
}
