package com.openchat.rtc.domain.vo;

import com.openchat.rtc.domain.RtcParticipantState;

/**
 * 呼叫参与者快照。
 */
public record RtcCallParticipantView(Long uid, RtcParticipantState state, Integer version) {
    public RtcCallParticipantView(Long uid, RtcParticipantState state) {
        this(uid, state, null);
    }
}
