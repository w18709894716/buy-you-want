package com.byw.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.byw.api")
@MapperScan("com.byw.user.mapper")
public class BywUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(BywUserApplication.class, args);
    }
}
