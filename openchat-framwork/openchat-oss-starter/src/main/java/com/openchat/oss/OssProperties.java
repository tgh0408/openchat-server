package com.openchat.oss;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "oss")
public class OssProperties {

    /**
     * 是否开启
     */
    Boolean enabled;

    /**
     * 存储对象服务器类型
     */
    OssType type;

    /**
     * OSS 访问端点，集群时需提供统一入口
     */
    String endpoint;

    /**
     * 客户端 S3 签名入口，供 PUT 上传及私有附件 GET 下载使用；未配置时沿用 endpoint
     */
    String uploadEndpoint;

    /**
     * 存储区域；未配置时由 SDK 查询，配置后必须与实际存储区域一致
     */
    String region;

    /**
     * 公开资源地址及消息稳定地址的前缀，不作为私有附件签名入口
     */
    String publicEndpoint;

    /**
     * 用户名
     */
    String accessKey;

    /**
     * 密码
     */
    String secretKey;

    /**
     * 存储桶
     */
    String bucketName;
}
