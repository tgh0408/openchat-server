package com.openchat.system.domain.vo.resp;

import java.util.List;

/** 显式隔离存储实体，不向面板暴露桶名与内部对象键。 */
public record AdminReleaseResp(String releaseId, String version, String clientName, String fileSize,
                               String sha256, List<String> updateInfo,
                               String publishedAt) {
}
