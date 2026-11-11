package com.openchat.rtc.service;

/** 呼叫条件更新失败，调用方须回滚并在新事务中重读后重试。 */
public final class RtcCallConflictException extends IllegalStateException {
    public RtcCallConflictException() {
        super("呼叫状态已变化");
    }
}
