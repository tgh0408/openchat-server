package com.openchat.user.service;

import com.openchat.common.exception.BusinessException;
import com.openchat.oss.MinIOTemplate;
import com.openchat.oss.OssProperties;
import com.openchat.oss.domain.OssReq;
import com.openchat.common.domain.enums.OssSceneEnum;
import com.openchat.common.service.MinioBucketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Iterator;
import java.util.Locale;

/** 仅供账号注册事务调用，不提供匿名通用上传或客户端对象路径接口。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RegistrationAvatarService {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final long MAX_PIXELS = 4_000_000L;
    private final MinIOTemplate minio;
    private final OssProperties properties;
    private final MinioBucketService minioBucketService;

    public String upload(MultipartFile file) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("注册头像必须在用户创建事务内保存");
        }
        byte[] bytes = normalize(file);
        // 注册尚无自增 uid，省略 ID 目录，沿用模板的年月与随机文件名，回滚只清理本次对象。
        String object = minio.generateAutoPath(OssReq.builder()
                .filePath(OssSceneEnum.AVATAR.getPath())
                .fileName("avatar.png")
                .build()).get("path");
        String bucket = minioBucketService.currentBucket();
        String url = publicUrl(object, bucket);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        minio.removeObject(bucket, object);
                    } catch (Exception exception) {
                        log.warn("注册回滚后头像清理失败，需核查对象: {}", object);
                    }
                } else if (status == STATUS_UNKNOWN) {
                    // 提交结果不明时不能删除可能已被已提交用户引用的对象。
                    log.warn("注册事务结果未知，保留头像待核查: {}", object);
                }
            }
        });
        try {
            minio.putObject(bucket, object, bytes, "image/png");
        } catch (Exception exception) {
            throw new BusinessException("头像上传失败，请稍后重试");
        }
        return url;
    }

    /** 校验正方形和既有资源上限，按原像素无损重编码，移除元数据和附加内容。 */
    private byte[] normalize(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new BusinessException("头像大小须为 1 字节至 10 MiB");
        }
        try (InputStream source = file.getInputStream()) {
            byte[] input = source.readNBytes(MAX_BYTES + 1);
            if (input.length == 0 || input.length > MAX_BYTES) {
                throw new BusinessException("头像大小须为 1 字节至 10 MiB");
            }
            try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(input))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw new BusinessException("头像仅支持有效的 JPG 或 PNG 图片");
                ImageReader reader = readers.next();
                try {
                    switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                        case "jpeg", "jpg", "png":
                            break;
                        default:
                            throw new BusinessException("头像仅支持 JPG 或 PNG 图片");
                    }
                    reader.setInput(stream, true, true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) {
                        throw new BusinessException("头像不能超过 400 万像素");
                    }
                    if (width != height) {
                        throw new BusinessException("头像必须为正方形，请先裁剪");
                    }
                    BufferedImage original = reader.read(0);
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    if (!ImageIO.write(original, "png", output)) throw new IOException("PNG encoder unavailable");
                    if (output.size() > MAX_BYTES) {
                        throw new BusinessException("裁剪头像超过 10 MiB，请缩小选区");
                    }
                    return output.toByteArray();
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new BusinessException("头像图片无法读取，请重新选择 JPG 或 PNG");
        }
    }

    private String publicUrl(String object, String bucket) {
        try {
            String endpoint = properties.getPublicEndpoint();
            if (endpoint == null || endpoint.isBlank()) throw new IllegalArgumentException();
            String url = minioBucketService.toPublicUrl(endpoint, minioBucketService.path(bucket, object));
            URI uri = URI.create(url);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null || url.length() > 255) {
                throw new IllegalArgumentException();
            }
            return url;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("头像存储地址配置无效");
        }
    }
}
