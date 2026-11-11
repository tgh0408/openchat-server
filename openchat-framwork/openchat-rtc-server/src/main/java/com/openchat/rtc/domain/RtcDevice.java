package com.openchat.rtc.domain;

/** 已通过登录校验的通话设备；平台与设备摘要共同确定归属。 */
public record RtcDevice(String terminal, String deviceId) {
    public RtcDevice {
        if (terminal == null || !terminal.matches("[A-Z]{2,16}")
                || deviceId == null || !deviceId.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("通话设备身份无效");
        }
    }

    /** 日志仅保留部分摘要，授权比较仍使用完整设备身份。 */
    public String fingerprint() {
        return deviceId.substring(0, 12);
    }
}
