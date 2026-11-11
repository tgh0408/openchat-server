package com.openchat.chat.domain.vo.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** 下载对象由服务端消息记录确定，不接受客户端提供的对象路径或用户身份。 */
@Data
public class MessageDownloadReq {
    @NotNull
    @Positive
    @Schema(description = "消息 ID，跨端使用十进制字符串")
    private Long msgId;

    @NotNull
    @Positive
    @Schema(description = "消息所属房间 ID，跨端使用十进制字符串")
    private Long roomId;
}
