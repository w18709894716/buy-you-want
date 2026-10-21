package com.byw.shop;

import com.alicp.jetcache.anno.config.EnableMethodCache;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.byw.api")
@MapperScan("com.byw.shop.mapper")
@EnableMethodCache(basePackages = "com.byw.shop")
public class BywShopApplication {
    public static void main(String[] args) {
        SpringApplication.run(BywShopApplication.class, args);
    }
}
