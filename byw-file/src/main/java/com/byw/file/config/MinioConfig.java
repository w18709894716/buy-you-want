package com.byw.file.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    private String endpoint = "http://localhost:9000";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin";
    private String bucketName = "byw-files";
    /** 对外访问地址（拼接返回给浏览器的文件 URL），为空时回退到 endpoint */
    private String publicUrl;

    /** 对外 URL：配了 public-url 就用它，否则用内部 endpoint */
    public String getPublicUrl() {
        return (publicUrl == null || publicUrl.isBlank()) ? endpoint : publicUrl;
    }

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
