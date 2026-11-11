package com.openchat.user.domain.vo.oss;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

/** 业务会话与真实存储状态；签名地址仅在单片请求中返回。 */
@Data
@AllArgsConstructor
public class MultipartUploadResp {
    private String sessionKey;
    private String uploadId;
    private String downloadUrl;
    private long fileSize;
    private int partSize;
    private boolean completed;
    private List<UploadedPart> parts;

    @Data
    @AllArgsConstructor
    public static class UploadedPart {
        private int partNumber;
        private String etag;
        private long size;
    }
}
