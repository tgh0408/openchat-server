package com.openchat.oss;

import io.minio.MinioClient;
import io.minio.MinioAsyncClient;
import lombok.SneakyThrows;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

@AutoConfiguration
@EnableConfigurationProperties(OssProperties.class)
@ConditionalOnExpression("${oss.enabled}")
@ConditionalOnProperty(value = "oss.type", havingValue = "minio")
public class MinIOConfiguration {

    @Bean
    @ConditionalOnMissingBean(MultipartClient.class)
    public MultipartClient multipartClient(OssProperties properties) {
        var builder = MinioAsyncClient.builder().endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey());
        if (StringUtils.hasText(properties.getRegion())) builder.region(properties.getRegion());
        return new MultipartClient(builder.build());
    }

    @Bean
    @SneakyThrows
    @ConditionalOnMissingBean(MinioClient.class)
    public MinioClient minioClient(OssProperties ossProperties) {
        return buildClient(ossProperties.getEndpoint(), ossProperties);
    }

    @Bean
    @ConditionalOnBean({MinioClient.class})
    @ConditionalOnMissingBean(MinIOTemplate.class)
    public MinIOTemplate minioTemplate(MinioClient minioClient, OssProperties ossProperties) {
        String uploadEndpoint = ossProperties.getUploadEndpoint();
        // PUT/GET 共用客户端 S3 入口；存储读写仍复用原 Bean，不替换已签名 URL。
        MinioClient uploadClient = !StringUtils.hasText(uploadEndpoint)
                || uploadEndpoint.equals(ossProperties.getEndpoint())
                ? minioClient : buildClient(uploadEndpoint, ossProperties);
        return new MinIOTemplate(minioClient, ossProperties, uploadClient);
    }

    /** 两个客户端共享凭据和实际区域，但使用各自的连接入口。 */
    private MinioClient buildClient(String endpoint, OssProperties properties) {
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(properties.getAccessKey(), properties.getSecretKey());
        if (StringUtils.hasText(properties.getRegion())) {
            builder.region(properties.getRegion());
        }
        return builder.build();
    }
}
