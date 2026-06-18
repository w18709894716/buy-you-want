package com.byw.common.mybatis.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
@Import(MyBatisPlusConfig.class)
public class MyBatisPlusAutoConfiguration {
}
