package com.byw.common.swagger.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@Import(SwaggerConfig.class)
public class SwaggerAutoConfiguration {
}
