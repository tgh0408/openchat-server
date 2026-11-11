package com.openchat.webrtc.domain.vo.resp;

import com.openchat.rtc.domain.vo.RtcCallView;

/** 可恢复的单聊快照；不携带旧媒体凭证或 SDP。 */
public record RtcOngoingCallView(RtcCallView call, String peerName) {}
