package com.byw.common.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnClass(NewTopic.class)
@Import(KafkaTopicConfig.class)
public class KafkaAutoConfiguration {
}
