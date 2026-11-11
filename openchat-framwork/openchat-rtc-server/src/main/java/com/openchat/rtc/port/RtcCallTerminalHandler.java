package com.openchat.rtc.port;

import com.openchat.rtc.domain.RtcCallRecord;

/**
 * RTC 呼叫进入终态后的业务扩展端口。
 *
 * <p>核心 RTC 模块不依赖聊天消息、群成员或 LiveKit。聊天服务可以在这里生成摘要消息，
 * 后续 webhook 也可以复用同一入口补充终态副作用。</p>
 */
public interface RtcCallTerminalHandler {

    void onTerminal(RtcCallRecord call, Long actorUid);

    static RtcCallTerminalHandler noop() {
        return (call, actorUid) -> {
        };
    }
}
