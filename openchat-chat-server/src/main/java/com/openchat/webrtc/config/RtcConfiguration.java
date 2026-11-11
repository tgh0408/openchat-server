package com.openchat.webrtc.config;

import com.openchat.rtc.service.RtcCallService;
import com.openchat.rtc.port.RtcCallRepository;
import com.openchat.rtc.port.RtcCallTerminalHandler;
import com.openchat.rtc.port.RtcEventPublisher;
import com.openchat.rtc.port.RtcIdGenerator;
import com.openchat.rtc.port.RtcPermissionPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RTC 核心服务装配。核心模块本身不声明 Spring Bean。
 */
@Configuration(proxyBeanMethods = false)
public class RtcConfiguration {
    @Bean
    public RtcCallService rtcCallService(
            RtcCallRepository repository,
            RtcPermissionPort permission,
            RtcEventPublisher eventPublisher,
            RtcIdGenerator idGenerator,
            RtcCallTerminalHandler terminalHandler) {
        return new RtcCallService(repository, permission, eventPublisher, idGenerator, terminalHandler);
    }

}
