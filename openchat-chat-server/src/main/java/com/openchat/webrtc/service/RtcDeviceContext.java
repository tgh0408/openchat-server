package com.openchat.webrtc.service;

import com.openchat.common.utils.RequestHolder;
import com.openchat.rtc.domain.RtcDevice;
import com.openchat.rtc.service.RtcOperationException;

/** HTTP 适配层读取已验证设备，后台任务不依赖请求线程上下文。 */
public final class RtcDeviceContext {
    private RtcDeviceContext() {}

    public static RtcDevice current() {
        var info = RequestHolder.get();
        if (info == null || info.getTerminal() == null || info.getDeviceId() == null)
            throw new RtcOperationException(RtcOperationException.Reason.DEVICE_UNKNOWN);
        return new RtcDevice(info.getTerminal().name(), info.getDeviceId());
    }
}
