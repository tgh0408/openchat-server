package com.openchat.webrtc.domain.vo.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * RTC 呼叫状态操作请求。
 */
@Data
public class RtcCallActionReq {
    @NotBlank
    private String clientRequestId;
    /** 单聊为整通版本；群个人操作为 participants 中本人成员版本，未入名单时为 1。 */
    @Positive
    private long expectedVersion;
}
