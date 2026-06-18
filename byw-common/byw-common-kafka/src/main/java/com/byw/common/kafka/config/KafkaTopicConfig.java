package com.byw.common.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import static com.byw.common.kafka.constant.KafkaTopics.*;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderStatusChangeTopic() {
        return TopicBuilder.name(ORDER_STATUS_CHANGE).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderCreateTopic() {
        return TopicBuilder.name(ORDER_CREATE).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderTimeoutCancelTopic() {
        return TopicBuilder.name(ORDER_TIMEOUT_CANCEL).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentResultTopic() {
        return TopicBuilder.name(PAYMENT_RESULT).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic logisticsUpdateTopic() {
        return TopicBuilder.name(LOGISTICS_UPDATE).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic productSyncEsTopic() {
        return TopicBuilder.name(PRODUCT_SYNC_ES).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic seckillOrderTopic() {
        return TopicBuilder.name(SECKILL_ORDER).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic userBehaviorTopic() {
        return TopicBuilder.name(USER_BEHAVIOR).partitions(3).replicas(1).build();
    }
}
