package com.openchat.common.service;

import com.openchat.common.exception.BusinessException;
import com.openchat.oss.OssProperties;
import com.openchat.oss.OssUrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;

/**
 * 从 OSS 配置读取当前 MinIO 桶，并负责数据库路径与对象 key 的转换，不访问系统表。
 */
@Service
@RequiredArgsConstructor
public class MinioBucketService {
    private final OssProperties ossProperties;

    public String currentBucket() {
        String bucket = ossProperties.getBucketName();
        return validateBucket(bucket);
    }

    public String validateBucket(String bucket) {
        if (!StringUtils.hasText(bucket) || bucket.contains("/") || bucket.contains("\\")
                || bucket.contains("?") || bucket.contains("#")) {
            throw new BusinessException("MinIO 桶未配置或配置无效");
        }
        return bucket.trim();
    }

    public String prefix() {
        return prefix(currentBucket());
    }

    public String prefix(String bucket) {
        return "/" + validateBucket(bucket) + "/";
    }

    public String path(String objectKey) {
        return path(currentBucket(), objectKey);
    }

    public String path(String bucket, String objectKey) {
        if (!StringUtils.hasText(objectKey) || objectKey.startsWith("/")) {
            throw new BusinessException("MinIO 对象路径无效");
        }
        return prefix(bucket) + objectKey;
    }

    public String objectKey(String path) {
        String prefix = prefix();
        if (!StringUtils.hasText(path) || !path.startsWith(prefix)
                || path.substring(prefix.length()).contains("..")) {
            throw new BusinessException("MinIO 对象路径无效");
        }
        return path.substring(prefix.length());
    }

    public String toPublicUrl(String endpoint, String path) {
        return OssUrlUtil.toPublicUrl(endpoint, path);
    }

    /**
     * 将数据库中的头像路径转换为公开地址，兼容历史上已保存的完整 URL。
     */
    public String toAvatarUrl(String avatar) {
        if (!StringUtils.hasText(avatar)) {
            return avatar;
        }
        try {
            URI source = URI.create(avatar);
            if (source.isAbsolute()) {
                return avatar;
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("用户头像地址无效");
        }
        String bucket = currentBucket();
        String normalized = avatar.replaceFirst("^/+", "");
        String path = normalized.startsWith(bucket + "/")
                ? "/" + normalized
                : path(bucket, normalized);
        return toPublicUrl(ossProperties.getPublicEndpoint(), path);
    }
}
