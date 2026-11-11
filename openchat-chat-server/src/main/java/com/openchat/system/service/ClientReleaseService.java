package com.openchat.system.service;

import com.openchat.common.domain.enums.OssSceneEnum;
import com.openchat.common.exception.BusinessException;
import com.openchat.common.service.MinioBucketService;
import com.openchat.common.utils.JsonUtils;
import com.openchat.oss.MinIOTemplate;
import com.openchat.oss.OssProperties;
import com.openchat.system.dao.ClientReleaseDao;
import com.openchat.system.domain.entity.ClientRelease;
import com.openchat.system.domain.vo.resp.AdminReleaseResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 客户端版本发布和安装包校验。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClientReleaseService {
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("(?<!\\d)v?(\\d+\\.\\d+\\.\\d+)");
    private static final List<String> PACKAGE_EXTENSIONS =
            List.of(".exe", ".msi", ".dmg", ".pkg", ".appimage", ".deb", ".zip");

    private final ClientReleaseDao clientReleaseDao;
    private final MinIOTemplate minioTemplate;
    private final OssProperties ossProperties;
    private final MinioBucketService minioBucketService;

    public ClientUpdateResp getLatest(boolean includeDownloadUrl) {
        ClientRelease release = clientReleaseDao.getLatest();
        if (release == null) {
            return null;
        }
        String downloadUrl = includeDownloadUrl ? publicDownloadUrl(release) : "";
        return toResponse(release, downloadUrl);
    }

    public List<ClientRelease> listReleases() {
        return clientReleaseDao.listReleases();
    }

    public List<AdminReleaseResp> listAdminReleases() {
        return clientReleaseDao.listReleases().stream().map(release -> new AdminReleaseResp(
                release.getId().toString(), release.getVersionName(), release.getClientName(),
                release.getFileSize().toString(), release.getSha256(), parseUpdateInfo(release.getUpdateInfo()),
                release.getPublishedAt() == null ? null : release.getPublishedAt().toString())).toList();
    }

    /**
     * 校验并上传安装包后追加发布记录，数据库和 MinIO 不属于同一事务。
     */
    public void publishUploaded(MultipartFile file, String updateInfo) {
        UploadedReleaseInfo info = inspect(file);
        String bucket = minioBucketService.currentBucket();
        String objectKey = buildObjectPath(info);
        ClientRelease release = new ClientRelease();
        release.setVersionName(info.version());
        release.setClientName(info.clientName());
        release.setBucketName(bucket);
        release.setObjectKey(objectKey);
        release.setFileSize(info.fileSize());
        release.setSha256(info.sha256());
        release.setUpdateInfo(normalizeUpdateInfo(updateInfo));
        try (InputStream inputStream = file.getInputStream()) {
            minioTemplate.putObject(bucket, objectKey, inputStream, info.fileSize(),
                    contentType(info.clientName()));
        } catch (IOException exception) {
            cleanupObject(bucket, objectKey);
            throw new BusinessException("读取客户端安装包失败");
        } catch (Exception exception) {
            cleanupObject(bucket, objectKey);
            throw new BusinessException("上传客户端安装包到 MinIO 失败");
        }
        try {
            clientReleaseDao.publish(release);
        } catch (DuplicateKeyException exception) {
            cleanupObject(bucket, objectKey);
            throw new BusinessException("该版本已发布，请使用新的版本号");
        } catch (RuntimeException exception) {
            // 提交异常可能发生在数据库已提交之后，不能删除可能已被引用的安装包。
            log.warn("客户端发布结果未确认，保留对象待核对: bucket={}, objectKey={}", bucket, objectKey);
            throw exception;
        }
    }

    /**
     * 按不可变发布 ID 下载；发布新版本不改变旧版本指向的安装包。
     */
    public String getPublicDownloadUrl(Long releaseId) {
        if (releaseId == null || releaseId <= 0) {
            throw new BusinessException("客户端更新文件不存在");
        }
        ClientRelease release = clientReleaseDao.getById(releaseId);
        if (release == null) {
            throw new BusinessException("客户端更新文件不存在");
        }
        return publicDownloadUrl(release);
    }

    private String publicDownloadUrl(ClientRelease release) {
        if (!StringUtils.hasText(release.getObjectKey())) {
            throw new BusinessException("客户端更新对象路径未配置");
        }
        if (!StringUtils.hasText(ossProperties.getPublicEndpoint())) {
            throw new BusinessException("MinIO 公开访问地址未配置");
        }
        return UriComponentsBuilder.fromUriString(trimTrailingSlash(ossProperties.getPublicEndpoint()))
                .path(minioBucketService.path(release.getBucketName(), release.getObjectKey()))
                .build()
                .encode()
                .toUriString();
    }

    public UploadedReleaseInfo inspect(MultipartFile file) {
        String clientName = safeFileName(file);
        String version = parseVersion(clientName);
        try {
            return new UploadedReleaseInfo(version, clientName, file.getSize(),
                    sha256(file.getInputStream()));
        } catch (IOException exception) {
            throw new BusinessException("读取客户端安装包失败");
        }
    }

    private ClientUpdateResp toResponse(ClientRelease release, String downloadUrl) {
        return new ClientUpdateResp(release.getId(), release.getVersionName(), release.getClientName(),
                release.getFileSize(), release.getSha256(), downloadUrl,
                parseUpdateInfo(release.getUpdateInfo()));
    }

    private String buildObjectPath(UploadedReleaseInfo info) {
        return OssSceneEnum.RELEASE.getPath() + "/" + info.version()
                + "/" + info.sha256() + "/" + UUID.randomUUID() + "-" + info.clientName();
    }

    private void cleanupObject(String bucket, String objectPath) {
        try {
            minioTemplate.removeObject(bucket, objectPath);
        } catch (Exception cleanupException) {
            log.warn("清理客户端更新对象失败: {}", objectPath, cleanupException);
        }
    }

    private String safeFileName(MultipartFile file) {
        if (file == null || file.isEmpty() || !StringUtils.hasText(file.getOriginalFilename())) {
            throw new BusinessException("请拖入客户端安装包");
        }
        String originalName = file.getOriginalFilename().replace('\\', '/');
        String clientName = originalName.substring(originalName.lastIndexOf('/') + 1);
        if (!StringUtils.hasText(clientName) || ".".equals(clientName) || "..".equals(clientName)) {
            throw new BusinessException("客户端安装包文件名无效");
        }
        if (clientName.length() > 255) {
            throw new BusinessException("客户端安装包文件名不能超过 255 个字符");
        }
        String lowerName = clientName.toLowerCase(Locale.ROOT);
        if (PACKAGE_EXTENSIONS.stream().noneMatch(lowerName::endsWith)) {
            throw new BusinessException("仅支持客户端安装包文件");
        }
        return clientName;
    }

    private String parseVersion(String clientName) {
        Matcher matcher = VERSION_PATTERN.matcher(clientName);
        if (!matcher.find()) {
            throw new BusinessException("无法从安装包文件名解析版本号");
        }
        String version = matcher.group(1);
        if (version.length() > 64) {
            throw new BusinessException("客户端版本号不能超过 64 个字符");
        }
        return version;
    }

    private String contentType(String clientName) {
        String lowerName = clientName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".exe")) {
            return "application/vnd.microsoft.portable-executable";
        }
        if (lowerName.endsWith(".msi")) {
            return "application/x-msi";
        }
        if (lowerName.endsWith(".dmg")) {
            return "application/x-apple-diskimage";
        }
        if (lowerName.endsWith(".pkg")) {
            return "application/octet-stream";
        }
        if (lowerName.endsWith(".zip")) {
            return "application/zip";
        }
        if (lowerName.endsWith(".appimage")) {
            return "application/octet-stream";
        }
        if (lowerName.endsWith(".deb")) {
            return "application/vnd.debian.binary-package";
        }
        return "application/octet-stream";
    }

    private String normalizeUpdateInfo(String updateInfo) {
        if (!StringUtils.hasText(updateInfo)) {
            return "[]";
        }
        String trimmed = updateInfo.trim();
        if (trimmed.startsWith("[")) {
            try {
                JsonUtils.toList(trimmed, String.class);
                return trimmed;
            } catch (RuntimeException ignored) {
                // 非法 JSON 按每行一条更新说明处理。
            }
        }
        List<String> items = Arrays.stream(trimmed.split("\\R"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        return JsonUtils.toStr(items);
    }

    private List<String> parseUpdateInfo(String updateInfo) {
        if (!StringUtils.hasText(updateInfo)) {
            return List.of();
        }
        try {
            return JsonUtils.toList(updateInfo, String.class);
        } catch (RuntimeException ignored) {
            return Arrays.stream(updateInfo.split("\\R"))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        }
    }

    private String sha256(InputStream input) throws IOException {
        try (InputStream source = input) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1024 * 1024];
            int length;
            while ((length = source.read(buffer)) >= 0) {
                digest.update(buffer, 0, length);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 不支持 SHA-256", exception);
        }
    }

    private String trimTrailingSlash(String value) {
        return value.replaceFirst("/+$", "");
    }

    public record UploadedReleaseInfo(String version, String clientName, long fileSize, String sha256) {
    }

    public record ClientUpdateResp(Long releaseId, String version, String clientName, long fileSize,
                                   String sha256, String downloadUrl, List<String> updateInfo) {
    }
}
