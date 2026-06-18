package com.byw.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.byw.api")
public class BywAdminApplication {
    public static void main(String[] args) {
        SpringApplication.run(BywAdminApplication.class, args);
    }
}
