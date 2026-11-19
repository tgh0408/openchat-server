package com.openchat.update.domain.vo.resp;

import java.util.List;

/** 客户端更新协议，Long 字段沿用全局字符串序列化。 */
public record ClientUpdateResp(Long releaseId, String version, String clientName, Long fileSize,
                               String sha256, String downloadUrl, List<String> updateInfo) {
}
