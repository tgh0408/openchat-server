package com.openchat.webrtc.domain.vo.resp;

import com.openchat.webrtc.domain.enums.RtcMediaTransportEnum;

import java.util.List;
import java.util.Map;

/** 仅返回当前已接听参与者可读的媒体协商信息。 */
public record RtcMediaView(
        String callId, RtcMediaTransportEnum transport, long version, String offerId,
        String offerSdp, String answerSdp, List<String> stunUrls,
        Map<String, RtcCameraState> cameraStates) {
}
