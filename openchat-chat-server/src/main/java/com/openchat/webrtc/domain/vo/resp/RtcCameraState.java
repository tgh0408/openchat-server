package com.openchat.webrtc.domain.vo.resp;

/** 摄像头实际开关状态，版本独立于呼叫和 SDP 协商。 */
public record RtcCameraState(boolean enabled, long version) {
}
