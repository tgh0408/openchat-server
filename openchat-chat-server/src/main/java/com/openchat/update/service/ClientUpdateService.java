package com.openchat.update.service;

import com.openchat.common.exception.BusinessException;
import com.openchat.oss.OssProperties;
import com.openchat.update.dao.ClientReleaseDao;
import com.openchat.update.domain.entity.ClientRelease;
import com.openchat.update.domain.vo.resp.ClientUpdateResp;
import com.openchat.user.domain.enums.TerminalEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;

/** 分布式聊天后端的只读更新入口，不调用 Dashboard，也不参与安装包发布。 */
@Service
@RequiredArgsConstructor
public class ClientUpdateService {
    private final ClientReleaseDao releaseDao;
    private final OssProperties ossProperties;
    private final JsonMapper jsonMapper;

    public ClientUpdateResp getLatest(TerminalEnum terminal) {
        ClientRelease release = releaseDao.getLatest(terminal);
        if (release == null) {
            return null;
        }
        // 元数据与下载地址来自同一次查询，覆盖发布时不会混用两个快照。
        return new ClientUpdateResp(release.getId(), release.getVersionName(), release.getClientName(),
                release.getFileSize(), release.getSha256(), publicDownloadUrl(release),
                parseUpdateInfo(release.getUpdateInfo()));
    }

    public String getPublicDownloadUrl(Long releaseId, TerminalEnum terminal) {
        if (releaseId == null || releaseId <= 0) {
            throw new BusinessException("客户端更新文件不存在");
        }
        ClientRelease release = releaseDao.getById(releaseId);
        if (release == null || release.getTerminal() != terminal) {
            throw new BusinessException("客户端更新文件不存在");
        }
        return publicDownloadUrl(release);
    }

    private String publicDownloadUrl(ClientRelease release) {
        String objectKey = release.getObjectKey();
        if (!StringUtils.hasText(objectKey) || objectKey.startsWith("/")) {
            throw new BusinessException("MinIO 对象路径无效");
        }
        String bucket = release.getBucketName();
        if (!StringUtils.hasText(bucket) || bucket.contains("/") || bucket.contains("\\")
                || bucket.contains("?") || bucket.contains("#")) {
            throw new BusinessException("MinIO 桶未配置或配置无效");
        }
        if (!StringUtils.hasText(ossProperties.getPublicEndpoint())) {
            throw new BusinessException("MinIO 公开访问地址未配置");
        }
        return UriComponentsBuilder.fromUriString(ossProperties.getPublicEndpoint().replaceFirst("/+$", ""))
                .path("/" + bucket.trim() + "/" + objectKey)
                .build().encode().toUriString();
    }

    private List<String> parseUpdateInfo(String updateInfo) {
        if (!StringUtils.hasText(updateInfo)) {
            return List.of();
        }
        try {
            return jsonMapper.readValue(updateInfo,
                    jsonMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (RuntimeException ignored) {
            return Arrays.stream(updateInfo.split("\\R"))
                    .map(String::trim).filter(StringUtils::hasText).toList();
        }
    }
}
