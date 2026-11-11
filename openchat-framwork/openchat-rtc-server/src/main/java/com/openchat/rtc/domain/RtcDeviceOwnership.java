package com.openchat.rtc.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/** 当前请求设备对本人通话参与记录的归属。 */
public enum RtcDeviceOwnership {
    UNASSIGNED, CURRENT_DEVICE, OTHER_DEVICE, UNKNOWN;

    @JsonValue
    public String getValue() {
        return name();
    }
}
