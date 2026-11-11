package com.openchat.rtc.service;

/** RTC 可识别业务结果，避免客户端通过中文提示猜测关闭或挂断动作。 */
public final class RtcOperationException extends RuntimeException {
    public enum Reason {
        ACCEPTED_ELSEWHERE(46001, "已在其他设备接听"),
        OPERATION_BUSY(46002, "通话操作处理中，请稍后重试"),
        STATE_CONFLICT(46003, "通话状态已变化，请重试"),
        RECOVERY_DEVICE_MISMATCH(46004, "接听设备不一致，通话已结束"),
        RECOVERY_DEVICE_UNKNOWN(46005, "无法确认原通话设备，通话已结束"),
        TERMINATION_FAILED(46006, "未能确认通话结束，请重试"),
        CALL_UNAVAILABLE(46007, "通话已结束或邀请已失效"),
        DEVICE_UNKNOWN(46008, "无法确认本设备的通话归属");

        private final int code;
        private final String message;

        Reason(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int code() { return code; }
        public String message() { return message; }
    }

    private final Reason reason;

    public RtcOperationException(Reason reason) {
        super(reason.message());
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
