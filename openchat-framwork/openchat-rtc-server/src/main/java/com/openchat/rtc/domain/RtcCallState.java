package com.openchat.rtc.domain;

/**
 * RTC 呼叫状态。
 */
public enum RtcCallState {
    RINGING,
    ACCEPTED,
    CONNECTED,
    REJECTED,
    CANCELED,
    ENDED,
    EXPIRED;

    public boolean terminal() {
        return switch (this) {
            case REJECTED, CANCELED, ENDED, EXPIRED -> true;
            default -> false;
        };
    }
}
