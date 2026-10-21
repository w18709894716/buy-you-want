package com.byw.common.redis.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import java.lang.reflect.Field;

/**
 * 为 JetCache 提供 fastjson2 的 valueEncoder / valueDecoder bean。
 *
 * <p><b>为什么需要它</b>：JetCache 2.7.9 的 {@code DefaultEncoderParser} 只内置
 * {@code JAVA} / {@code KRYO} / {@code KRYO5} 三种 valueEncoder 名称，**没有 FASTJSON2**
 * （只有 keyConvertor 原生支持 {@code FASTJSON2}）。配置里写 {@code valueEncoder: FASTJSON2}
 * 会在启动时抛 {@code CacheConfigException: not supported:FASTJSON2}，服务直接起不来。
 *
 * <p>而 {@code DefaultSpringEncoderParser} 支持 {@code bean:<beanName>} 语法引用任意 Spring bean，
 * 所以这里把 JetCache 自带的 fastjson2 编解码器注册成 bean，配置写成
 * {@code valueEncoder: bean:fastjson2ValueEncoder}，保持 JSON 序列化不变。
 *
 * <p><b>为什么用反射而不是直接引用类</b>：这个模块（byw-common-redis）会被**所有**服务依赖，
 * 而 JetCache 只有 3 个服务真正使用。若在此声明编译期依赖 jetcache-core，会给所有服务带入一个
 * 用不到的库，并且一旦 IDE 项目模型没有重新导入就会编译报"程序包 com.alicp.jetcache.support 不存在"。
 * 用 {@code @ConditionalOnClass(name=...)} + 反射取单例，既不用编译期依赖，也只在
 * JetCache 存在时才注册这两个 bean。
 */
@AutoConfiguration
@ConditionalOnClass(name = {
        "com.alicp.jetcache.support.Fastjson2ValueEncoder",
        "com.alicp.jetcache.support.Fastjson2ValueDecoder"
})
public class JetCacheEncoderAutoConfiguration {

    private static final String ENCODER_CLASS = "com.alicp.jetcache.support.Fastjson2ValueEncoder";
    private static final String DECODER_CLASS = "com.alicp.jetcache.support.Fastjson2ValueDecoder";

    /** 对应配置 {@code jetcache.remote.default.valueEncoder: bean:fastjson2ValueEncoder} */
    @Bean
    public Object fastjson2ValueEncoder() throws ReflectiveOperationException {
        return singleton(ENCODER_CLASS);
    }

    /** 对应配置 {@code jetcache.remote.default.valueDecoder: bean:fastjson2ValueDecoder} */
    @Bean
    public Object fastjson2ValueDecoder() throws ReflectiveOperationException {
        return singleton(DECODER_CLASS);
    }

    /** 取 JetCache 提供的单例 INSTANCE（与它内部默认用法一致，保证编解码参数成对） */
    private static Object singleton(String className) throws ReflectiveOperationException {
        Class<?> type = Class.forName(className);
        Field field = type.getField("INSTANCE");
        return field.get(null);
    }
}
