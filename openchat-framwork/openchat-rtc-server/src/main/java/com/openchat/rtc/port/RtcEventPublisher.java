package com.openchat.rtc.port;

import com.openchat.rtc.domain.vo.RtcEvent;

/**
 * RTC 事件发布端口。
 */
public interface RtcEventPublisher {
    void publish(RtcEvent event);
}
