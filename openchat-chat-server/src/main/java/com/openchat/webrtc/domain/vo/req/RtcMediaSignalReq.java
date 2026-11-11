package com.openchat.webrtc.domain.vo.req;

import com.openchat.webrtc.domain.enums.RtcMediaSignalTypeEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 非 trickle SDP 含本次 ICE 候选；请求 ID 沿用现有客户端幂等标识。 */
@Getter
@Setter
public class RtcMediaSignalReq {
    /** 本次操作类型，接口使用枚举名称作为字符串值。 */
    @NotNull
    private RtcMediaSignalTypeEnum kind;
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{1,128}")
    private String clientRequestId;
    @Pattern(regexp = "[A-Za-z0-9_-]{1,128}")
    private String offerId;
    @Size(max = 60000)
    private String sdp;
    private Boolean enabled;
    private Long expectedCameraVersion;
}
