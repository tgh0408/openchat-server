package com.openchat.webrtc.domain.vo.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * RTC 群视频席位操作请求。
 */
@Data
public class RtcVideoActionReq {
    @NotNull
    private Boolean enabled;
}
