package com.openchat.rtc.domain.vo;

import com.openchat.rtc.domain.RtcDevice;
/**
 * 呼叫状态操作命令。
 */
public record CallActionCommand(String clientRequestId, long expectedVersion, RtcDevice device) {
    public CallActionCommand(String clientRequestId, long expectedVersion) {
        this(clientRequestId, expectedVersion, null);
    }
    public CallActionCommand {
        if (clientRequestId == null || clientRequestId.isBlank()
                || clientRequestId.length() > 128 || clientRequestId.indexOf('\0') >= 0
                || expectedVersion < 1) {
            throw new IllegalArgumentException("呼叫操作参数无效");
        }
    }
}
