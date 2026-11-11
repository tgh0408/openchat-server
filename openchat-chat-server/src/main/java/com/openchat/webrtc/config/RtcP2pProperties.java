package com.openchat.webrtc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/** 直连只使用 STUN；穿透失败统一回退 LiveKit，不偷偷使用 TURN 中继。 */
@Data
@Component
@ConfigurationProperties(prefix = "openchat.rtc.p2p")
public class RtcP2pProperties {
    private boolean enabled = true;
    private List<String> stunUrls = List.of();
}
