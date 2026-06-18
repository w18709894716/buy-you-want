package com.byw.common.elasticsearch.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;

@AutoConfiguration
@ConditionalOnClass(ElasticsearchConfiguration.class)
@Import(ElasticsearchConfig.class)
public class ElasticsearchAutoConfiguration {
}
