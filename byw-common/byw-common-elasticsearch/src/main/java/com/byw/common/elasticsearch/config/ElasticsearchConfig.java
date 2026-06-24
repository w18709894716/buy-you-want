package com.byw.common.elasticsearch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;

import java.util.Arrays;

@Configuration
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Value("${spring.elasticsearch.uris:localhost:9200}")
    private String esUris;

    @Override
    public ClientConfiguration clientConfiguration() {
        // connectedTo 只需要 host:port，这里兼容用户写成 http://host:port 的情况
        String[] endpoints = Arrays.stream(esUris.split(","))
                .map(String::trim)
                .map(uri -> uri.replaceFirst("^(?i)(https?://)", ""))
                .toArray(String[]::new);

        return ClientConfiguration.builder()
                .connectedTo(endpoints)
                .build();
    }
}
