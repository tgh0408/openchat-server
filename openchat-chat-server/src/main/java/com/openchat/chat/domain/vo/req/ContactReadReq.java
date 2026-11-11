package com.openchat.chat.domain.vo.req;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** 客户端实际阅读到的消息，不以延迟刷新的房间摘要作为上限。 */
@Data
public class ContactReadReq {
    @NotNull
    @Positive
    @Min(0)
    private Long roomId;
    @NotNull
    @Positive
    @Min(0)
    private Long lastReadMsgId;
}
