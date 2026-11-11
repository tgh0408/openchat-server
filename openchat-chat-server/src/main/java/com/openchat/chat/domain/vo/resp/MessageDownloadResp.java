package com.openchat.chat.domain.vo.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

/** 短期下载凭证，不持久化到消息记录。 */
@Data
@Builder
public class MessageDownloadResp {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long msgId;
    @Schema(description = "附件 GET 预签名 URL，须完整使用，不发送业务 JWT")
    private String downloadUrl;
    @Schema(description = "视频封面 GET 预签名 URL，其他消息为空")
    private String thumbDownloadUrl;
    @Schema(description = "签名有效秒数，自 URL 中的签发时间起计算")
    private Integer expiresIn;
}
